package com.simposio.taskeer.data.calendar

import android.content.ContentUris
import android.content.ContentResolver
import android.content.ContentValues
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.simposio.taskeer.data.repository.CalendarRepository
import com.simposio.taskeer.domain.calendar.CalendarRead
import com.simposio.taskeer.domain.calendar.DeviceCalendar
import com.simposio.taskeer.domain.calendar.DeviceEvent
import com.simposio.taskeer.domain.calendar.EventDraft
import com.simposio.taskeer.domain.calendar.EventSpan
import com.simposio.taskeer.domain.date.TimeZoneProvider
import com.simposio.taskeer.domain.model.DomainError
import com.simposio.taskeer.domain.model.DomainResult
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DeviceCalendarRepository(
    private val resolver: ContentResolver,
    private val timeZone: TimeZoneProvider,
    private val hasRead: () -> Boolean,
    private val hasWrite: () -> Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CalendarRepository {

    override fun observeCalendars(): Flow<CalendarRead<List<DeviceCalendar>>> = observeProvider(::queryCalendars)

    override fun observeEvents(from: LocalDate, toExclusive: LocalDate): Flow<CalendarRead<List<DeviceEvent>>> =
        observeProvider { queryEvents(from, toExclusive) }

    override suspend fun createEvent(draft: EventDraft): DomainResult<Long> = withContext(io) {
        if (!hasWrite()) return@withContext permissionDenied()
        try {
            val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, valuesFor(draft))
            if (uri == null) writeFailed() else DomainResult.Success(ContentUris.parseId(uri))
        } catch (denied: SecurityException) {
            permissionDenied()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            writeFailed()
        }
    }

    private fun <T> observeProvider(query: () -> List<T>): Flow<CalendarRead<List<T>>> = flow {
        if (!hasRead()) {
            emit(CalendarRead.NoPermission)
            return@flow
        }
        emitAll(providerChanges().conflate().map { read(query) })
    }.catch { cause -> emit(failureOf(cause)) }

    private fun providerChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    private suspend fun <T> read(query: () -> List<T>): CalendarRead<List<T>> = withContext(io) {
        try {
            if (hasRead()) CalendarRead.Ok(query()) else CalendarRead.NoPermission
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failureOf(failure)
        }
    }

    private fun failureOf(cause: Throwable): CalendarRead<Nothing> = when (cause) {
        is SecurityException -> CalendarRead.NoPermission
        is CancellationException -> throw cause
        else -> CalendarRead.Failed(DomainError.Unknown("CALENDAR_UNAVAILABLE", "No se pudo leer el calendario"))
    }

    private fun queryCalendars(): List<DeviceCalendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val order = "${CalendarContract.Calendars.ACCOUNT_NAME}, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME}"
        val cursor = resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, order)
        return requireCursor(cursor).use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    add(
                        DeviceCalendar(
                            id = rows.getLong(0),
                            name = rows.getString(1).orEmpty(),
                            accountName = rows.getString(2).orEmpty(),
                            colorArgb = calendarColor(rows.getInt(3)),
                            isWritable = rows.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                        ),
                    )
                }
            }
        }
    }

    // Margen de un día a cada lado: los eventos de todo el día están a medianoche UTC y la zona local puede
    // quedar a cualquier lado; el filtrado exacto lo hace el dominio.
    private fun queryEvents(from: LocalDate, toExclusive: LocalDate): List<DeviceEvent> {
        val zone = timeZone.current()
        val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = toExclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
        )
        return requireCursor(CalendarContract.Instances.query(resolver, projection, begin, end)).use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    val eventId = rows.getLong(0)
                    val beginMillis = rows.getLong(1)
                    add(
                        DeviceEvent(
                            instanceKey = eventInstanceKey(eventId, beginMillis),
                            eventId = eventId,
                            calendarId = rows.getLong(5),
                            title = rows.getString(3).orEmpty(),
                            span = eventSpanOf(beginMillis, rows.getLong(2), rows.getInt(4) != 0),
                        ),
                    )
                }
            }
        }
    }

    private fun requireCursor(cursor: Cursor?): Cursor = checkNotNull(cursor) { "El proveedor de calendario no respondió" }

    private fun valuesFor(draft: EventDraft): ContentValues = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, requireNotNull(draft.calendarId))
        put(CalendarContract.Events.TITLE, draft.title.trim())
        when (val span = draft.span) {
            is EventSpan.Timed -> {
                put(CalendarContract.Events.DTSTART, span.start.toEpochMilli())
                put(CalendarContract.Events.DTEND, span.end.toEpochMilli())
                put(CalendarContract.Events.EVENT_TIMEZONE, timeZone.current().id)
                put(CalendarContract.Events.ALL_DAY, 0)
            }
            is EventSpan.AllDay -> {
                put(CalendarContract.Events.DTSTART, utcMidnightMillis(span.start))
                put(CalendarContract.Events.DTEND, utcMidnightMillis(span.endExclusive))
                put(CalendarContract.Events.EVENT_TIMEZONE, ALL_DAY_TIMEZONE)
                put(CalendarContract.Events.ALL_DAY, 1)
            }
        }
    }

    private fun permissionDenied(): DomainResult.Failure = DomainResult.Failure(
        DomainError.Forbidden("CALENDAR_PERMISSION_DENIED", "Falta el permiso para escribir en el calendario"),
    )

    private fun writeFailed(): DomainResult.Failure = DomainResult.Failure(
        DomainError.Unknown("CALENDAR_WRITE_FAILED", "No se pudo guardar el evento"),
    )

    private companion object {
        const val ALL_DAY_TIMEZONE = "UTC"
    }
}

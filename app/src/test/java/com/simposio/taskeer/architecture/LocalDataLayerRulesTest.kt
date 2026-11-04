package com.simposio.taskeer.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataLayerRulesTest {

    private val sourcesRoot: File = resolveSourcesRoot()

    @Test
    fun dataLocalSinBorradoFisico() {
        val violations = dataLocalSources().flatMap { file ->
            listOf("@Delete", "DELETE FROM")
                .filter { file.readText().contains(it) }
                .map { "${file.relativeTo(sourcesRoot)} contiene $it" }
        }

        assertTrue("el borrado siempre es lógico; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun appDatabaseDeclaraMigracionesExplicitas() {
        val source = File(sourcesRoot, "data/local/AppDatabase.kt").readText()

        assertTrue("AppDatabase debe declarar MIGRATIONS", source.contains("MIGRATIONS"))
        assertTrue("AppDatabase debe aplicar las migraciones con addMigrations", source.contains("addMigrations("))
        assertTrue("AppDatabase debe exportar el esquema", source.contains("exportSchema = true"))
        assertFalse("no se permite borrado destructivo", source.contains("fallbackToDestructive"))
    }

    @Test
    fun todaEntidadImplementaLaConvencion() {
        val entitySources = dataLocalSources().filter { it.readText().contains("@Entity") }

        assertTrue("debe existir al menos una entidad en data/local/", entitySources.isNotEmpty())
        val violations = entitySources
            .filterNot { it.readText().contains("SyncableEntity") }
            .map { "${it.relativeTo(sourcesRoot)} no implementa SyncableEntity" }

        assertTrue("toda entidad debe implementar la convención; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun domainYUiSinTiposDeDataLocal() {
        val forbiddenTokens = listOf(
            "androidx.room",
            "AppDatabase",
            "BoardEntity",
            "BoardDao",
            "BoardMapping",
            "ColumnEntity",
            "ColumnDao",
            "ColumnMapping",
            "TaskEntity",
            "TaskDao",
            "TaskMapping",
            "SubtaskEntity",
            "SubtaskDao",
            "SubtaskMapping",
            "MyDayEntryEntity",
            "MyDayDao",
            "MyDayRow",
            "MyDayMapping",
            "ReminderEntity",
            "ReminderDao",
            "ReminderMapping",
            "AppNotificationEntity",
            "AppNotificationDao",
            "AppNotificationMapping",
            "SyncableEntity",
        )

        val violations = listOf("domain", "ui").flatMap { layer ->
            kotlinSources(File(sourcesRoot, layer)).flatMap { file ->
                forbiddenTokens
                    .filter { file.readText().contains(it) }
                    .map { "${file.relativeTo(sourcesRoot)} referencia $it" }
            }
        }

        assertTrue("domain/ y ui/ no deben conocer la capa local; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun contratosDeRepositorioNeutros() {
        val forbiddenTokens = listOf("androidx.room", "data/local/", "data/remote/", "androidx.datastore")
        val contracts = File(sourcesRoot, "data/repository")
            .listFiles { file -> file.isFile && file.name.endsWith("Repository.kt") && !file.name.endsWith("Impl.kt") }
            ?.toList()
            .orEmpty()

        assertTrue("debe existir al menos un contrato de repositorio", contracts.isNotEmpty())
        val violations = contracts.flatMap { file ->
            forbiddenTokens
                .filter { file.readText().contains(it) }
                .map { "${file.relativeTo(sourcesRoot)} referencia $it" }
        }

        assertTrue("los contratos deben ser neutros; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun uiSinImplementacionesConcretas() {
        val forbiddenTokens = listOf(
            "androidx.room",
            "androidx.datastore",
            "RepositoryImpl",
            "AppDatabase",
            "BoardDao",
            "BoardEntity",
            "BoardMapping",
            "ColumnDao",
            "ColumnEntity",
            "ColumnMapping",
            "TaskDao",
            "TaskEntity",
            "TaskMapping",
            "SubtaskEntity",
            "SubtaskDao",
            "SubtaskMapping",
            "MyDayEntryEntity",
            "MyDayDao",
            "MyDayRow",
            "MyDayMapping",
            "ReminderEntity",
            "ReminderDao",
            "ReminderMapping",
            "AppNotificationEntity",
            "AppNotificationDao",
            "AppNotificationMapping",
            "SyncableEntity",
        )

        val violations = kotlinSources(File(sourcesRoot, "ui")).flatMap { file ->
            forbiddenTokens
                .filter { file.readText().contains(it) }
                .map { "${file.relativeTo(sourcesRoot)} referencia $it" }
        }

        assertTrue("ui/ no debe tocar almacén ni implementaciones; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun reminderEntityYDaoSiguenLaConvencionSinBorradoFisico() {
        val entity = File(sourcesRoot, "data/local/ReminderEntity.kt").readText()
        val dao = File(sourcesRoot, "data/local/ReminderDao.kt").readText()

        assertTrue("ReminderEntity debe implementar SyncableEntity", entity.contains(": SyncableEntity"))
        assertFalse("ReminderDao no puede borrar físicamente", dao.contains("@Delete") || dao.contains("DELETE FROM"))
    }

    @Test
    fun reminderContratosNeutrosSinTiposDeAndroid() {
        val forbiddenTokens = listOf("android.", "androidx.", "data/local/")
        val contracts = listOf(
            "ReminderRepository.kt",
            "ReminderScheduler.kt",
            "ReminderNotifier.kt",
            "ReminderCheckpointStore.kt",
        )
            .map { File(sourcesRoot, "data/repository/$it") }

        val violations = contracts.flatMap { file ->
            forbiddenTokens
                .filter { file.readText().contains(it) }
                .map { "${file.relativeTo(sourcesRoot)} referencia $it" }
        }

        assertTrue("los contratos de recordatorios deben ser neutros; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun androidDeRecordatoriosConfinadoEnDataReminderYAppContainerFactory() {
        val platformTokens = listOf("AlarmManager", "NotificationManager", "BroadcastReceiver", "PendingIntent")

        val violations = listOf("domain", "ui", "data/repository", "data/local").flatMap { layer ->
            kotlinSources(File(sourcesRoot, layer)).flatMap { file ->
                platformTokens
                    .filter { file.readText().contains(it) }
                    .map { "${file.relativeTo(sourcesRoot)} referencia $it" }
            }
        }

        assertTrue("la plataforma de alarmas vive en data/reminder/; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun dominioDeRecordatoriosSinAndroidNiRoom() {
        val files = listOf(
            "domain/reminder/ReminderPolicy.kt",
            "domain/reminder/ReminderShortcuts.kt",
            "domain/reminder/ReminderRules.kt",
            "domain/model/NotificationEvent.kt",
            "domain/model/ReminderLead.kt",
            "domain/model/ReminderSettings.kt",
            "domain/model/ReminderCandidate.kt",
            "domain/model/Reminder.kt",
        ).map { File(sourcesRoot, it) }

        val violations = files.flatMap { file ->
            listOf("android.", "androidx.", "data/local", "kotlinx.coroutines")
                .filter { file.readText().contains(it) }
                .map { "${file.relativeTo(sourcesRoot)} referencia $it" }
        }

        assertTrue("el dominio de recordatorios debe ser Kotlin puro; violaciones: $violations", violations.isEmpty())
    }

    @Test
    fun conciliadorYDisparadoresSinAndroidYSoloNotificanPorElContrato() {
        val reconciler = File(sourcesRoot, "data/reminder/ReminderReconciler.kt").readText()
        val triggers = File(sourcesRoot, "data/reminder/ReminderTriggers.kt").readText()

        listOf(reconciler, triggers).forEach {
            assertFalse("sin tipos de Android", it.contains("import android.") || it.contains("import androidx."))
        }
        assertFalse("no debe tocar el sistema de notificaciones", reconciler.contains("NotificationManager"))
        assertTrue("debe emitir por el contrato ReminderNotifier", reconciler.contains("notifier.notify("))
        assertTrue("debe recibir el notificador como contrato", reconciler.contains("private val notifier: ReminderNotifier"))
    }

    @Test
    fun soloElNotificadorDeLaPlataformaUsaElSistemaDeNotificaciones() {
        val offenders = kotlinSources(sourcesRoot)
            .filter { it.readText().contains("NotificationManagerCompat.from(") || it.readText().contains("NotificationCompat.Builder") }
            .map { it.name }
            .filterNot { it == "NotificationReminderNotifier.kt" || it == "AppContainerFactory.kt" }

        assertTrue("las notificaciones salen solo por NotificationReminderNotifier; violaciones: $offenders", offenders.isEmpty())
    }

    @Test
    fun appShellYaNoAlojaElSelectorDeTema() {
        val shell = File(sourcesRoot, "ui/shell/AppShell.kt").readText()

        assertFalse(shell.contains("ThemeMenu"))
        assertFalse(shell.contains("themeMode"))
        assertFalse(shell.contains("DropdownMenu"))
    }

    @Test
    fun noQuedanRestosDelDisenoPorRecordatorio() {
        assertFalse(File(sourcesRoot, "data/reminder/ReminderDispatcher.kt").exists())
        val taskRepository = File(sourcesRoot, "data/repository/TaskRepositoryImpl.kt").readText()
        assertFalse(taskRepository.contains("ReminderScheduler"))
    }

    private fun dataLocalSources(): List<File> = kotlinSources(File(sourcesRoot, "data/local"))

    private fun kotlinSources(directory: File): List<File> =
        directory.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun resolveSourcesRoot(): File {
        val relativePath = "app/src/main/java/com/simposio/taskeer"
        val userDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        return generateSequence(userDir) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isDirectory }
            ?: error("No encontré $relativePath subiendo desde $userDir")
    }
}

package com.simposio.taskeer.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.simposio.taskeer.domain.sync.BackgroundKind
import java.time.Duration
import java.util.concurrent.TimeUnit

// Las sobrecargas con Duration de WorkManager exigen API 26; con minSdk 24 se usa la de milisegundos.
class WorkManagerBackgroundScheduler(context: Context) : BackgroundSyncScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)
    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun enqueuePush() {
        val request = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
            .setConstraints(connected)
            .setInputData(workDataOf(BackgroundSyncWorker.KEY_KIND to BackgroundKind.PUSH.name))
            .build()
        workManager.enqueueUniqueWork(PUSH_WORK, ExistingWorkPolicy.KEEP, request)
    }

    override fun enqueueRetry(delay: Duration) {
        val request = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
            .setConstraints(connected)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(BackgroundSyncWorker.KEY_KIND to BackgroundKind.RETRY.name))
            .build()
        workManager.enqueueUniqueWork(RETRY_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun enqueuePeriodicPull(interval: Duration) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(interval.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setInputData(workDataOf(BackgroundSyncWorker.KEY_KIND to BackgroundKind.PULL.name))
            .build()
        workManager.enqueueUniquePeriodicWork(PULL_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelAll() {
        listOf(PUSH_WORK, RETRY_WORK, PULL_WORK).forEach(workManager::cancelUniqueWork)
    }

    private companion object {
        const val PUSH_WORK = "taskeer.sync.push"
        const val RETRY_WORK = "taskeer.sync.retry"
        const val PULL_WORK = "taskeer.sync.pull"
    }
}

package com.simposio.taskeer.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.simposio.taskeer.domain.sync.BackgroundDecision
import com.simposio.taskeer.domain.sync.BackgroundKind

class BackgroundSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val provider = applicationContext as? BackgroundSyncRunnerProvider ?: return Result.failure()
        val kind = inputData.getString(KEY_KIND)?.let { name -> BackgroundKind.entries.firstOrNull { it.name == name } }
            ?: return Result.failure()
        return when (provider.backgroundSyncRunner.run(kind, runAttemptCount)) {
            BackgroundDecision.SUCCESS -> Result.success()
            BackgroundDecision.RETRY -> Result.retry()
            BackgroundDecision.FAILURE -> Result.failure()
        }
    }

    companion object {
        const val KEY_KIND = "kind"
    }
}

package com.simposio.taskeer.di

import android.util.Log
import com.simposio.taskeer.BuildConfig
import com.simposio.taskeer.data.sync.SyncDebugLog

internal object AndroidSyncDebugLog : SyncDebugLog {
    private const val TAG = "TaskeerSync"

    override fun line(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}

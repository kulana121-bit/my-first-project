package com.notes.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.notes.app.NotesApplication

class DeviceSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as NotesApplication).repository
        return runCatching {
            val report = repo.syncNow()
            if (report.errors.isEmpty()) Result.success() else Result.retry()
        }.getOrElse { Result.retry() }
    }
}

package com.nextgenmatrix.data.db.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Retries pending Matrix sends from the local outbox with exponential backoff.
 * Wired for Hilt worker injection in a follow-up; currently a safe no-op scaffold.
 */
class SendOutboxWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = Result.success()
}

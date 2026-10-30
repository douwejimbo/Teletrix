package com.nextgenmatrix.data.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nextgenmatrix.matrix.MatrixRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var matrix: MatrixRepository

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!matrix.hasPersistedSession()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                matrix.restoreSession()
            } finally {
                pending.finish()
            }
        }
    }
}

package com.nextgenmatrix.matrix

import org.matrix.rustcomponents.sdk.LogLevel
import org.matrix.rustcomponents.sdk.TracingConfiguration
import org.matrix.rustcomponents.sdk.initPlatform

/** Routes Rust SDK logs to logcat. Call once from Application.onCreate. */
object MatrixTracing {
    fun install(verbose: Boolean) {
        runCatching {
            initPlatform(
                TracingConfiguration(
                    logLevel = if (verbose) LogLevel.TRACE else LogLevel.INFO,
                    traceLogPacks = emptyList(),
                    extraTargets = emptyList(),
                    writeToStdoutOrSystem = true,
                    writeToFiles = null,
                    sentryConfig = null,
                ),
                false,
            )
        }
    }
}

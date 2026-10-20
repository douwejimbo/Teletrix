package com.nextgenmatrix.app

import android.app.Application
import com.nextgenmatrix.data.push.UnifiedPushRegistrar
import com.nextgenmatrix.feature.chat.installAnimatedImageLoader
import com.nextgenmatrix.matrix.MatrixTracing
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NgApplication : Application() {
    @Inject lateinit var unifiedPushRegistrar: UnifiedPushRegistrar

    override fun onCreate() {
        super.onCreate()
        // Flip to true to stream Rust crypto traces to logcat (tag org.matrix.rust.sdk).
        MatrixTracing.install(verbose = false)
        installAnimatedImageLoader()
        unifiedPushRegistrar.registerDefaultDistributor()
    }
}

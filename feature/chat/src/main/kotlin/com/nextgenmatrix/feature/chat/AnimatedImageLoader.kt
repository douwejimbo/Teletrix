package com.nextgenmatrix.feature.chat

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder

/** Coil loader that plays GIF and animated WebP. TGS is handled separately with Lottie. */
fun installAnimatedImageLoader() {
    SingletonImageLoader.setSafe { context: Context ->
        ImageLoader.Builder(context)
            .components {
                add(AnimatedImageDecoder.Factory())
            }
            .build()
    }
}

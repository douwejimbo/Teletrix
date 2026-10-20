package com.nextgenmatrix.feature.chat

/**
 * Authenticated Matrix media fetch for Compose (thumbnails, full media, avatars).
 * Suspends are safe to call from [androidx.compose.runtime.produceState] / [kotlinx.coroutines].
 */
data class MatrixChatMedia(
    val loadThumbnail: suspend (String) -> ByteArray?,
    val loadContent: suspend (String) -> ByteArray?,
    val loadAvatar: suspend (String?) -> ByteArray?,
)

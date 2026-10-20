@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nextgenmatrix.feature.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.nextgenmatrix.core.model.TimelineContentKind
import com.nextgenmatrix.core.model.TimelineMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

private sealed interface BubbleVisual {
    data class Still(val bitmap: ImageBitmap) : BubbleVisual
    data class Animated(val bytes: ByteArray) : BubbleVisual
    data class LottieJson(val json: String) : BubbleVisual
}

@Composable
fun TimelineVisualMedia(
    message: TimelineMessage,
    media: MatrixChatMedia,
    bodyColor: Color,
    onOpen: () -> Unit = {},
    onLongPress: () -> Unit = {},
) {
    val wantsFull = message.contentKind == TimelineContentKind.Sticker || message.isAnimatedMedia
    val source = if (wantsFull) {
        message.mediaContentJson ?: message.mediaSourceJson
    } else {
        message.mediaSourceJson
    }
    if (source == null) {
        Text(message.body, style = MaterialTheme.typography.bodyLarge, color = bodyColor)
        return
    }
    val visual by produceState<BubbleVisual?>(null, source, message.eventId, wantsFull) {
        value = withContext(Dispatchers.IO) {
            val bytes = if (wantsFull) {
                media.loadContent(source) ?: media.loadThumbnail(source)
            } else {
                media.loadThumbnail(source)
            } ?: return@withContext null
            classifyBubbleMedia(bytes, message.mediaMimeType)
        }
    }
    val lottieJson = (visual as? BubbleVisual.LottieJson)?.json
    val composition by rememberLottieComposition(
        LottieCompositionSpec.JsonString(lottieJson ?: "{}"),
    )
    val context = LocalContext.current
    Column {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.08f))
                .fillMaxWidth()
                .heightIn(min = 72.dp, max = 240.dp)
                .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = visual) {
                is BubbleVisual.Still ->
                    Image(
                        bitmap = current.bitmap,
                        contentDescription = message.body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        contentScale = ContentScale.Fit,
                    )

                is BubbleVisual.Animated ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(current.bytes)
                            .memoryCacheKey(message.eventId.value)
                            .build(),
                        contentDescription = message.body,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        contentScale = ContentScale.Fit,
                    )

                is BubbleVisual.LottieJson ->
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        modifier = Modifier.size(160.dp),
                    )

                null ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = bodyColor,
                    )
            }
        }
        if (message.body.isNotBlank() && message.body != "Photo" && message.body != "Sticker") {
            Spacer(Modifier.height(6.dp))
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
            )
        }
    }
}

private fun classifyBubbleMedia(bytes: ByteArray, mime: String?): BubbleVisual? {
    val m = mime?.lowercase().orEmpty()
    val gzip = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
    val lottieMime = m.contains("gzip") || m.contains("lottie") || m.contains("tgsticker")
    if (gzip || lottieMime) {
        val json = runCatching { gunzip(bytes) }.getOrNull()
            ?: bytes.takeIf { it.firstOrNull() == '{'.code.toByte() }?.toString(Charsets.UTF_8)
        if (!json.isNullOrBlank() && json.trimStart().startsWith("{")) {
            return BubbleVisual.LottieJson(json)
        }
    }
    if (isGif(bytes) || m.contains("gif") || isAnimatedWebp(bytes)) {
        return BubbleVisual.Animated(bytes)
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return BubbleVisual.Still(bitmap.asImageBitmap())
}

private fun gunzip(bytes: ByteArray): String =
    GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }

private fun isGif(bytes: ByteArray): Boolean {
    if (bytes.size < 6) return false
    val head = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
    return head == "GIF87a" || head == "GIF89a"
}

private fun isAnimatedWebp(bytes: ByteArray): Boolean {
    if (bytes.size < 16) return false
    val riff = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
    val webp = bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII)
    if (riff != "RIFF" || webp != "WEBP") return false
    val window = bytes.copyOfRange(12, minOf(bytes.size, 64 * 1024))
    return indexOfAscii(window, "ANIM") >= 0
}

private fun indexOfAscii(haystack: ByteArray, needle: String): Int {
    val n = needle.toByteArray(Charsets.US_ASCII)
    if (n.isEmpty() || haystack.size < n.size) return -1
    for (i in 0..haystack.size - n.size) {
        var ok = true
        for (j in n.indices) {
            if (haystack[i + j] != n[j]) {
                ok = false
                break
            }
        }
        if (ok) return i
    }
    return -1
}

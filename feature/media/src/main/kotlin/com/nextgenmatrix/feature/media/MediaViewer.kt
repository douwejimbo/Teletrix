package com.nextgenmatrix.feature.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.nextgenmatrix.core.model.TimelineContentKind
import com.nextgenmatrix.core.model.TimelineMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import kotlin.math.max

private val viewerTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

private sealed interface Loaded {
    data object Loading : Loaded
    data object Failed : Loaded
    class Ready(val bytes: ByteArray) : Loaded
}

/**
 * Full-screen viewer. Images, videos and stickers page left and right, images zoom, and audio,
 * voice messages and files open in the same screen with save and share actions.
 */
@Composable
fun MediaViewer(
    items: List<TimelineMessage>,
    startIndex: Int,
    senderLabel: (TimelineMessage) -> String,
    loadContent: suspend (String) -> ByteArray?,
    onPlaybackStarted: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    val bytesCache = remember { mutableStateMapOf<String, ByteArray>() }
    var chromeVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) { zoomed = false }
    DisposableEffect(Unit) {
        onDispose { File(context.cacheDir, "viewer_play").deleteRecursively() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !zoomed,
                key = { items[it].eventId.value },
            ) { page ->
                val message = items[page]
                val isCurrent = pagerState.currentPage == page
                val loaded by rememberLoaded(message, loadContent) { id, bytes -> bytesCache[id] = bytes }
                val ready = (loaded as? Loaded.Ready)?.bytes
                when (message.contentKind) {
                    TimelineContentKind.Image, TimelineContentKind.Sticker ->
                        ImagePage(
                            message = message,
                            loaded = loaded,
                            onZoomChanged = { if (isCurrent) zoomed = it },
                            onTap = { chromeVisible = !chromeVisible },
                        )

                    TimelineContentKind.Video ->
                        VideoPage(message, ready, loaded is Loaded.Failed, isCurrent, onPlaybackStarted)

                    TimelineContentKind.Audio ->
                        AudioPage(
                            message = message,
                            senderLabel = senderLabel(message),
                            bytes = ready,
                            failed = loaded is Loaded.Failed,
                            isCurrent = isCurrent,
                            onPlaybackStarted = onPlaybackStarted,
                        )

                    else ->
                        FilePage(
                            message = message,
                            bytes = ready,
                            failed = loaded is Loaded.Failed,
                        )
                }
            }

            val current = items[pagerState.currentPage]
            val currentBytes = bytesCache[current.eventId.value]
            val pageKeepsChrome = current.contentKind != TimelineContentKind.Image &&
                current.contentKind != TimelineContentKind.Sticker

            AnimatedVisibility(
                visible = chromeVisible || pageKeepsChrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                ViewerTopBar(
                    title = senderLabel(current),
                    subtitle = viewerTimeFmt.format(
                        Instant.ofEpochMilli(current.timestampMs).atZone(ZoneId.systemDefault()),
                    ),
                    counter = if (items.size > 1) "${pagerState.currentPage + 1} / ${items.size}" else null,
                    actionsEnabled = currentBytes != null,
                    onClose = onDismiss,
                    onShare = { currentBytes?.let { shareMedia(context, current, it) } },
                    onSave = { currentBytes?.let { saveMedia(context, current, it) } },
                )
            }

            val caption = captionOf(current)
            if (caption != null) {
                AnimatedVisibility(
                    visible = chromeVisible || pageKeepsChrome,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))),
                            )
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Text(caption, color = Color.White, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private val defaultLabels = setOf("photo", "video", "audio", "sticker", "voice message", "attachment")

private fun captionOf(message: TimelineMessage): String? {
    if (message.contentKind != TimelineContentKind.Image && message.contentKind != TimelineContentKind.Video) {
        return null
    }
    val body = message.body.trim()
    if (body.isEmpty() || body.lowercase() in defaultLabels) return null
    if (body == message.mediaFileName) return null
    return body
}

@Composable
private fun ViewerTopBar(
    title: String,
    subtitle: String,
    counter: String?,
    actionsEnabled: Boolean,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(subtitle, counter).joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.7f),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
        IconButton(onClick = onShare, enabled = actionsEnabled) {
            Icon(
                Icons.Outlined.Share,
                contentDescription = "Share",
                tint = Color.White.copy(alpha = if (actionsEnabled) 1f else 0.35f),
            )
        }
        IconButton(onClick = onSave, enabled = actionsEnabled) {
            Icon(
                Icons.Outlined.Download,
                contentDescription = "Save",
                tint = Color.White.copy(alpha = if (actionsEnabled) 1f else 0.35f),
            )
        }
    }
}

@Composable
private fun rememberLoaded(
    message: TimelineMessage,
    loadContent: suspend (String) -> ByteArray?,
    onReady: (String, ByteArray) -> Unit,
): State<Loaded> =
    produceState<Loaded>(Loaded.Loading, message.eventId) {
        val source = message.mediaContentJson ?: message.mediaSourceJson
        if (source == null) {
            value = Loaded.Failed
            return@produceState
        }
        val bytes = withContext(Dispatchers.IO) { loadContent(source) }
        if (bytes != null && bytes.isNotEmpty()) {
            onReady(message.eventId.value, bytes)
            value = Loaded.Ready(bytes)
        } else {
            value = Loaded.Failed
        }
    }

@Composable
private fun CenterNotice(loading: Boolean, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
        } else {
            Text(text, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
    }
}

// ---------------------------------------------------------------- images

private sealed interface Visual {
    class Still(val bitmap: ImageBitmap) : Visual
    class Animated(val bytes: ByteArray) : Visual
    class Lottie(val json: String) : Visual
}

@Composable
private fun ImagePage(
    message: TimelineMessage,
    loaded: Loaded,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    when (loaded) {
        Loaded.Loading -> CenterNotice(loading = true, text = "")
        Loaded.Failed -> CenterNotice(loading = false, text = "Could not load this image")
        is Loaded.Ready -> {
            val visual by produceState<Visual?>(null, loaded.bytes) {
                value = withContext(Dispatchers.Default) { decodeVisual(loaded.bytes, message.mediaMimeType) }
            }
            when (val v = visual) {
                null -> CenterNotice(loading = true, text = "")
                is Visual.Lottie -> {
                    val composition by rememberLottieComposition(LottieCompositionSpec.JsonString(v.json))
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(onClick = onTap),
                        contentAlignment = Alignment.Center,
                    ) {
                        LottieAnimation(
                            composition = composition,
                            iterations = LottieConstants.IterateForever,
                            modifier = Modifier.size(280.dp),
                        )
                    }
                }

                else -> ZoomableFrame(onZoomChanged = onZoomChanged, onTap = onTap) {
                    when (v) {
                        is Visual.Still ->
                            Image(
                                bitmap = v.bitmap,
                                contentDescription = message.body,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )

                        is Visual.Animated -> {
                            val context = LocalContext.current
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(v.bytes).build(),
                                contentDescription = message.body,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}

/** Pinch to zoom, drag to pan when zoomed, double tap to toggle, single tap for [onTap]. */
@Composable
private fun ZoomableFrame(
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
    content: @Composable () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    LaunchedEffect(scale > 1.01f) { onZoomChanged(scale > 1.01f) }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1.01f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2 || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val next = (scale * zoom).coerceIn(1f, 6f)
                            scale = next
                            offset = if (next <= 1.01f) Offset.Zero else clamp(offset + pan, next)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun decodeVisual(bytes: ByteArray, mime: String?): Visual? {
    val m = mime?.lowercase().orEmpty()
    val gzip = bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
    if (gzip || m.contains("gzip") || m.contains("lottie") || m.contains("tgsticker")) {
        val json = runCatching {
            GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull() ?: bytes.takeIf { it.firstOrNull() == '{'.code.toByte() }?.toString(Charsets.UTF_8)
        if (!json.isNullOrBlank() && json.trimStart().startsWith("{")) return Visual.Lottie(json)
    }
    if (isGif(bytes) || m.contains("gif") || isAnimatedWebp(bytes)) return Visual.Animated(bytes)

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > 2600) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    return Visual.Still(applyExifRotation(decoded, bytes).asImageBitmap())
}

private fun applyExifRotation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
    val degrees = runCatching {
        when (
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (degrees == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun isGif(bytes: ByteArray): Boolean {
    if (bytes.size < 6) return false
    val head = String(bytes, 0, 6, Charsets.US_ASCII)
    return head == "GIF87a" || head == "GIF89a"
}

private fun isAnimatedWebp(bytes: ByteArray): Boolean {
    if (bytes.size < 16) return false
    if (String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, Charsets.US_ASCII) != "WEBP") {
        return false
    }
    val window = String(bytes, 12, minOf(bytes.size, 64 * 1024) - 12, Charsets.ISO_8859_1)
    return window.contains("ANIM")
}

// ---------------------------------------------------------------- video and audio

private suspend fun writePlayFile(context: Context, message: TimelineMessage, bytes: ByteArray): File =
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "viewer_play").apply { mkdirs() }
        val safe = message.eventId.value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        File(dir, "$safe.${extensionFor(message, bytes)}").also { if (!it.exists()) it.writeBytes(bytes) }
    }

@Composable
private fun VideoPage(
    message: TimelineMessage,
    bytes: ByteArray?,
    failed: Boolean,
    isCurrent: Boolean,
    onPlaybackStarted: () -> Unit,
) {
    if (failed) {
        CenterNotice(loading = false, text = "Could not load this video")
        return
    }
    if (bytes == null) {
        CenterNotice(loading = true, text = "")
        return
    }
    val context = LocalContext.current
    val file by produceState<File?>(null, bytes) { value = writePlayFile(context, message, bytes) }
    val current = file
    if (current == null || !isCurrent) {
        CenterNotice(loading = isCurrent, text = "")
        return
    }
    val player = remember(current) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(current)))
            playWhenReady = true
            prepare()
        }
    }
    LaunchedEffect(player) { onPlaybackStarted() }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                controllerShowTimeoutMs = 2500
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format("%d:%02d", total / 60, total % 60)
}

@Composable
private fun AudioPage(
    message: TimelineMessage,
    senderLabel: String,
    bytes: ByteArray?,
    failed: Boolean,
    isCurrent: Boolean,
    onPlaybackStarted: () -> Unit,
) {
    val context = LocalContext.current
    val isVoice = message.isVoiceMessage
    val title = when {
        isVoice -> "Voice message"
        !message.mediaFileName.isNullOrBlank() -> message.mediaFileName!!
        else -> message.body.ifBlank { "Audio" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(132.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isVoice) Icons.Filled.Mic else Icons.Filled.Audiotrack,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(60.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            senderLabel,
            color = Color.White.copy(alpha = 0.7f),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(28.dp))

        when {
            failed -> Text("Could not load this audio", color = Color.White.copy(alpha = 0.8f))
            bytes == null -> CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            else -> {
                val file by produceState<File?>(null, bytes) { value = writePlayFile(context, message, bytes) }
                val current = file
                if (current == null) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                } else {
                    AudioControls(
                        file = current,
                        fallbackDurationMs = message.mediaDurationMs,
                        isCurrent = isCurrent,
                        onPlaybackStarted = onPlaybackStarted,
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioControls(
    file: File,
    fallbackDurationMs: Long?,
    isCurrent: Boolean,
    onPlaybackStarted: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    var playing by remember(player) { mutableStateOf(false) }
    var positionMs by remember(player) { mutableLongStateOf(0L) }
    var durationMs by remember(player) { mutableLongStateOf(fallbackDurationMs ?: 0L) }
    var dragging by remember(player) { mutableStateOf(false) }
    var dragFraction by remember(player) { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    player.seekTo(0)
                    player.pause()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, isCurrent) {
        if (isCurrent) {
            onPlaybackStarted()
            player.playWhenReady = true
        } else {
            player.pause()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            if (!dragging) positionMs = player.currentPosition
            val d = player.duration
            if (d > 0) durationMs = d
            delay(200)
        }
    }

    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Slider(
        value = if (dragging) dragFraction else fraction,
        onValueChange = {
            dragging = true
            dragFraction = it
        },
        onValueChangeFinished = {
            if (durationMs > 0) player.seekTo((dragFraction * durationMs).toLong())
            dragging = false
        },
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val shown = if (dragging) (dragFraction * durationMs).toLong() else positionMs
        Text(formatClock(shown), color = Color.White.copy(alpha = 0.7f), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        Text(formatClock(durationMs), color = Color.White.copy(alpha = 0.7f), style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
    }
    Spacer(Modifier.height(12.dp))
    IconButton(
        onClick = { if (playing) player.pause() else player.play() },
        modifier = Modifier.size(72.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White, contentColor = Color.Black),
    ) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            modifier = Modifier.size(40.dp),
        )
    }
}

// ---------------------------------------------------------------- files

@Composable
private fun FilePage(
    message: TimelineMessage,
    bytes: ByteArray?,
    failed: Boolean,
) {
    val context = LocalContext.current
    val name = message.mediaFileName ?: message.body.removePrefix("📎").trim().ifBlank { "File" }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(120.dp)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            name,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(
            message.mediaSizeBytes?.let { formatBytes(it) },
            message.mediaMimeType?.takeIf { it.isNotBlank() },
        ).joinToString("  ·  ")
        if (meta.isNotEmpty()) {
            Text(meta, color = Color.White.copy(alpha = 0.7f), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(28.dp))
        when {
            failed -> Text("Could not download this file", color = Color.White.copy(alpha = 0.8f))
            bytes == null -> CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            else -> {
                Button(onClick = { openMedia(context, message, bytes) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Open")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { saveMedia(context, message, bytes) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Download, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.size(8.dp))
                    Text("Save to device", color = Color.White)
                }
            }
        }
    }
}

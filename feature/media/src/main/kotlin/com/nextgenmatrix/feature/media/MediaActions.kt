package com.nextgenmatrix.feature.media

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.nextgenmatrix.core.model.TimelineContentKind
import com.nextgenmatrix.core.model.TimelineMessage
import java.io.File
import java.util.Locale

private val replyDefaults = setOf("photo", "video", "audio", "sticker", "voice message", "attachment")

/** File name to use when a viewed item is saved or shared. */
internal fun fileNameFor(message: TimelineMessage, bytes: ByteArray?): String {
    message.mediaFileName?.takeIf { it.isNotBlank() }?.let { return sanitizeFileName(it) }
    val stem = message.body
        .removePrefix("📎")
        .trim()
        .takeIf { it.isNotBlank() && it.lowercase(Locale.ROOT) !in replyDefaults }
        ?.take(60)
        ?: when (message.contentKind) {
            TimelineContentKind.Image -> "photo"
            TimelineContentKind.Video -> "video"
            TimelineContentKind.Audio -> if (message.isVoiceMessage) "voice-message" else "audio"
            TimelineContentKind.Sticker -> "sticker"
            else -> "file"
        }
    val clean = sanitizeFileName(stem)
    return if (clean.contains('.')) clean else clean + "." + extensionFor(message, bytes)
}

private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifBlank { "file" }

internal fun extensionFor(message: TimelineMessage, bytes: ByteArray?): String {
    val mime = message.mediaMimeType?.lowercase(Locale.ROOT).orEmpty()
    fun has(vararg starts: Int): Boolean =
        bytes != null && bytes.size >= starts.size && starts.indices.all { (bytes[it].toInt() and 0xFF) == starts[it] }
    return when {
        mime.contains("jpeg") || mime.contains("jpg") || has(0xFF, 0xD8) -> "jpg"
        mime.contains("png") || has(0x89, 0x50, 0x4E, 0x47) -> "png"
        mime.contains("gif") || has(0x47, 0x49, 0x46, 0x38) -> "gif"
        mime.contains("webp") -> "webp"
        mime.contains("heic") -> "heic"
        mime.contains("webm") -> "webm"
        mime.contains("matroska") || mime.contains("mkv") -> "mkv"
        mime.contains("quicktime") -> "mov"
        mime.contains("mp4") && message.contentKind == TimelineContentKind.Audio -> "m4a"
        mime.contains("mp4") -> "mp4"
        mime.contains("mpeg") && message.contentKind == TimelineContentKind.Audio -> "mp3"
        mime.contains("ogg") || mime.contains("opus") -> "ogg"
        mime.contains("wav") -> "wav"
        mime.contains("flac") -> "flac"
        mime.contains("aac") -> "aac"
        mime.contains("pdf") -> "pdf"
        mime.contains("zip") -> "zip"
        mime.contains("gzip") -> "tgs"
        has(0x49, 0x44, 0x33) -> "mp3"
        has(0x4F, 0x67, 0x67, 0x53) -> "ogg"
        has(0x66, 0x4C, 0x61, 0x43) -> "flac"
        message.contentKind == TimelineContentKind.Video -> "mp4"
        message.contentKind == TimelineContentKind.Image -> "jpg"
        message.contentKind == TimelineContentKind.Audio -> if (message.isVoiceMessage) "ogg" else "mp3"
        else -> "bin"
    }
}

internal fun mimeFor(message: TimelineMessage, fileName: String): String {
    message.mediaMimeType?.takeIf { it.contains('/') }?.let { return it }
    return when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "pdf" -> "application/pdf"
        else -> when (message.contentKind) {
            TimelineContentKind.Image -> "image/*"
            TimelineContentKind.Video -> "video/*"
            TimelineContentKind.Audio -> "audio/*"
            else -> "application/octet-stream"
        }
    }
}

/** Copies the bytes to the app cache and returns a content Uri other apps may read. */
private fun cacheUriFor(context: Context, name: String, bytes: ByteArray): Uri {
    val dir = File(context.cacheDir, "viewer_shared").apply { mkdirs() }
    val file = File(dir, name)
    file.writeBytes(bytes)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

internal fun shareMedia(context: Context, message: TimelineMessage, bytes: ByteArray) {
    val name = fileNameFor(message, bytes)
    val uri = cacheUriFor(context, name, bytes)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeFor(message, name))
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

internal fun openMedia(context: Context, message: TimelineMessage, bytes: ByteArray) {
    val name = fileNameFor(message, bytes)
    val uri = cacheUriFor(context, name, bytes)
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mimeFor(message, name))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(view) }.onFailure {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

/** Saves into the shared Pictures, Movies, Music or Downloads folder (no storage permission needed). */
internal fun saveMedia(context: Context, message: TimelineMessage, bytes: ByteArray) {
    val name = fileNameFor(message, bytes)
    val mime = mimeFor(message, name)
    val (collection, folder) = when (message.contentKind) {
        TimelineContentKind.Image, TimelineContentKind.Sticker ->
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_PICTURES}/Teletrix"
        TimelineContentKind.Video ->
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/Teletrix"
        TimelineContentKind.Audio ->
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MUSIC}/Teletrix"
        else ->
            MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
    }
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val target = runCatching { resolver.insert(collection, values) }.getOrNull()
    val saved = target != null && runCatching {
        resolver.openOutputStream(target)?.use { it.write(bytes) } ?: error("no stream")
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(target, values, null, null)
    }.isSuccess
    if (!saved && target != null) runCatching { resolver.delete(target, null, null) }
    Toast.makeText(
        context,
        if (saved) "Saved to $folder" else "Could not save the file",
        Toast.LENGTH_SHORT,
    ).show()
}

internal fun formatBytes(size: Long): String {
    if (size < 1024) return "$size B"
    val kb = size / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

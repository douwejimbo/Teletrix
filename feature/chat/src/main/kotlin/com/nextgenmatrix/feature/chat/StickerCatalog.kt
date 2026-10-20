package com.nextgenmatrix.feature.chat

import android.content.Context
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

data class BuiltInSticker(
    val id: String,
    val emoji: String,
    val assetPath: String,
)

data class StickerPack(
    val id: String,
    val title: String,
    val stickers: List<BuiltInSticker>,
) {
    val tabEmoji: String get() = stickers.first().emoji
}

/**
 * Animated sticker packs shipped with the app. Artwork is Noto Emoji Animation (Google, CC BY 4.0).
 * Files are Lottie JSON; they are gzipped to Telegram's TGS-style payload when sent.
 */
object StickerCatalog {
    val packs: List<StickerPack> = listOf(
        pack(
            "faces",
            "Faces",
            listOf(
                "1f600", "1f602", "1f60d", "1f618", "1f62d", "1f621", "1f914", "1f60e",
                "1f973", "1f929", "1f97a", "1f92d", "1f608", "1f47b", "1f480", "1f47d",
            ),
        ),
        pack(
            "gestures",
            "Gestures",
            listOf(
                "1f44d", "1f44e", "1f44f", "1f64f", "1f91d", "2764", "1f4a5", "1f525",
                "1f389", "1f48e", "1f31f", "1f4af", "1f4a9", "1f383", "1f680", "1f4a3",
            ),
        ),
        pack(
            "animals",
            "Animals",
            listOf(
                "1f431", "1f415", "1f981", "1f98a", "1f43b", "1f43c", "1f427", "1f438",
                "1f984", "1f98b", "1f419", "1f40d", "1f409", "1f996", "1f41d", "1f433",
            ),
        ),
        pack(
            "treats",
            "Treats",
            listOf(
                "1f355", "1f354", "1f353", "1f352", "1f34e", "1f382", "2615", "1f339",
                "1f338", "1f337", "1f490", "1f30d", "1f308", "1f32a", "26c4", "1f30a",
            ),
        ),
    )

    private val byId: Map<String, BuiltInSticker> =
        packs.flatMap { it.stickers }.associateBy { it.id }

    fun find(id: String): BuiltInSticker? = byId[id]

    fun packById(id: String): StickerPack? = packs.firstOrNull { it.id == id }
}

object RecentStickers {
    private const val PREFS = "ng_sticker_recents"
    private const val KEY = "order"
    private const val MAX = 24

    fun load(context: Context): List<BuiltInSticker> {
        val raw = prefs(context).getString(KEY, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.split(',').mapNotNull { StickerCatalog.find(it) }
    }

    fun remember(context: Context, sticker: BuiltInSticker): List<BuiltInSticker> {
        val current = load(context).map { it.id }
        val next = (listOf(sticker.id) + current.filter { it != sticker.id }).take(MAX)
        prefs(context).edit().putString(KEY, next.joinToString(",")).apply()
        return next.mapNotNull { StickerCatalog.find(it) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

fun stickerPayload(context: Context, sticker: BuiltInSticker): ByteArray {
    val raw = context.assets.open(sticker.assetPath).use { it.readBytes() }
    val out = ByteArrayOutputStream(raw.size / 2)
    GZIPOutputStream(out).use { it.write(raw) }
    return out.toByteArray()
}

private fun pack(id: String, title: String, codes: List<String>): StickerPack =
    StickerPack(
        id = id,
        title = title,
        stickers = codes.map { code ->
            BuiltInSticker(
                id = "$id/$code",
                emoji = String(Character.toChars(code.toInt(16))),
                assetPath = "stickers/$id/$code.json",
            )
        },
    )

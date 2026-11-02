package com.nextgenmatrix.matrix.timeline

import com.nextgenmatrix.core.model.MatrixEventId
import com.nextgenmatrix.core.model.MatrixRoomId
import com.nextgenmatrix.core.model.MatrixUserId
import com.nextgenmatrix.core.model.MessageReaction
import com.nextgenmatrix.core.model.TimelineContentKind
import com.nextgenmatrix.core.model.TimelineMessage
import org.matrix.rustcomponents.sdk.EmbeddedEventDetails
import org.matrix.rustcomponents.sdk.EventOrTransactionId
import org.matrix.rustcomponents.sdk.EventTimelineItem
import org.matrix.rustcomponents.sdk.FormattedBody
import org.matrix.rustcomponents.sdk.MessageFormat
import org.matrix.rustcomponents.sdk.MediaSource
import org.matrix.rustcomponents.sdk.MessageContent
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.MsgLikeContent
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.MessageLikeEventType
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.use
import android.util.Log
import org.json.JSONObject
import java.net.URLDecoder

internal object TimelineMessageMapper {
    private const val TAG = "NgTimeline"
    fun mapItems(
        roomId: MatrixRoomId,
        ownUserId: String,
        items: List<TimelineItem>,
    ): List<TimelineMessage> =
        items.mapNotNull { item ->
            val eventItem = item.asEvent() ?: return@mapNotNull null
            runCatching { mapEvent(roomId, ownUserId, eventItem) }
                .onFailure { Log.w(TAG, "mapEvent failed", it) }
                .getOrNull()
        }

    /** Plain body line for room-list previews (reuses message mapping). */
    internal fun messagePreviewBody(mc: MessageContent, ownUserId: String): String =
        extractFromMessageContent(mc, ownUserId, forPreview = true).body

    private fun mapEvent(
        roomId: MatrixRoomId,
        ownUserId: String,
        item: EventTimelineItem,
    ): TimelineMessage? {
        val content = item.content
        if (content !is TimelineItemContent.MsgLike) return null
        val msgLike = content.content
        val payload = extractPayloadFromMsgLike(msgLike, ownUserId, item) ?: return null
        val reply = extractReplyQuote(msgLike, ownUserId)
        val threadRoot = msgLike.threadRoot?.takeIf { it.isNotBlank() }?.let { MatrixEventId(it) }
        val eventId = when (val id = item.eventOrTransactionId) {
            is EventOrTransactionId.EventId -> MatrixEventId(id.eventId)
            is EventOrTransactionId.TransactionId -> MatrixEventId("txn:${id.transactionId}")
            else -> return null
        }
        val sender = MatrixUserId(item.sender)
        val ts = item.timestamp.toLong()
        val (senderName, senderAvatar) = extractSender(item)
        val readBy = item.readReceipts.orEmpty()
            .filter { (userId, _) -> userId != ownUserId && userId != item.sender }
            .entries
            .sortedByDescending { it.value.timestamp?.toLong() ?: 0L }
            .map { it.key }
        return TimelineMessage(
            eventId = eventId,
            roomId = roomId,
            senderId = sender,
            senderDisplayName = senderName,
            senderAvatarMxc = senderAvatar,
            body = payload.body,
            timestampMs = ts,
            isOwn = item.sender == ownUserId,
            contentKind = payload.kind,
            mediaSourceJson = payload.mediaJson,
            mediaContentJson = payload.contentJson,
            mediaDurationMs = payload.durationMs,
            mediaMimeType = payload.mime,
            isAnimatedMedia = payload.animated,
            isVoiceMessage = payload.isVoice,
            mentionsMe = payload.mentionsMe,
            readByUserIds = readBy,
            readReceiptOtherCount = readBy.size,
            replyToEventId = reply?.eventId,
            replyToPreviewAuthor = reply?.authorLabel,
            replyToPreviewBody = reply?.bodyPreview,
            replyToParentIsOwn = reply?.parentIsOwn == true,
            replyToSenderId = reply?.senderId,
            mediaFileName = payload.fileName,
            mediaSizeBytes = payload.sizeBytes,
            threadRootEventId = threadRoot,
            latestForeignReactionMs = latestForeignReactionMs(msgLike, ownUserId),
            reactions = mapReactions(msgLike, ownUserId),
        )
    }

    private fun mapReactions(msgLike: MsgLikeContent, ownUserId: String): List<MessageReaction> =
        msgLike.reactions
            .asSequence()
            .mapNotNull { reaction ->
                val senders = reaction.senders
                if (senders.isEmpty()) return@mapNotNull null
                val key = reaction.key.trim()
                if (key.isEmpty()) return@mapNotNull null
                MessageReaction(
                    key = key,
                    count = senders.size,
                    includedMe = senders.any { it.senderId == ownUserId },
                )
            }
            .sortedByDescending { it.count }
            .toList()

    private fun latestForeignReactionMs(msgLike: MsgLikeContent, ownUserId: String): Long? =
        msgLike.reactions
            .asSequence()
            .flatMap { it.senders }
            .filter { it.senderId != ownUserId }
            .maxOfOrNull { it.timestamp.toLong() }

    private data class Payload(
        val body: String,
        val kind: TimelineContentKind,
        val mediaJson: String? = null,
        val contentJson: String? = null,
        val durationMs: Long? = null,
        val mime: String? = null,
        val animated: Boolean = false,
        val isVoice: Boolean = false,
        val mentionsMe: Boolean = false,
        val fileName: String? = null,
        val sizeBytes: Long? = null,
    )

    private data class ReplyQuote(
        val eventId: MatrixEventId,
        val authorLabel: String,
        val bodyPreview: String,
        val parentIsOwn: Boolean,
        val senderId: String? = null,
    )

    private fun extractSender(item: EventTimelineItem): Pair<String?, String?> {
        return when (val p = item.senderProfile) {
            is ProfileDetails.Ready -> {
                val name = p.displayName?.takeIf { it.isNotBlank() }
                val avatar = p.avatarUrl?.takeIf { it.isNotBlank() }
                name to avatar
            }
            else -> null to null
        }
    }

    private fun profileLabel(senderId: String, profile: ProfileDetails?): String {
        return when (val p = profile) {
            is ProfileDetails.Ready ->
                p.displayName?.takeIf { it.isNotBlank() }
                    ?: senderId.substringAfter("@").substringBefore(":").ifBlank { senderId }
            else -> senderId.substringAfter("@").substringBefore(":").ifBlank { senderId }
        }
    }

    private fun ellipsize(s: String, max: Int = 160): String {
        val t = s.trim()
        if (t.length <= max) return t
        return t.take(max).trimEnd() + "…"
    }

    private fun extractReplyQuote(msgLike: MsgLikeContent, ownUserId: String): ReplyQuote? {
        val handle = msgLike.inReplyTo ?: return null
        return runCatching {
            handle.use { details ->
                val rawId = details.eventId()?.takeIf { it.isNotBlank() } ?: return@use null
                val eid = MatrixEventId(rawId)
                when (val embedded = details.event()) {
                    is EmbeddedEventDetails.Ready -> {
                        val parentSender = embedded.sender
                        val parentIsOwn = parentSender == ownUserId
                        val authorLabel = if (parentIsOwn) "You" else profileLabel(parentSender, embedded.senderProfile)
                        val preview = ellipsize(
                            previewBodyFromTimelineContent(embedded.content, ownUserId),
                        )
                        ReplyQuote(eid, authorLabel, preview, parentIsOwn, parentSender)
                    }
                    is EmbeddedEventDetails.Pending ->
                        ReplyQuote(eid, "…", "Original message…", false)
                    is EmbeddedEventDetails.Unavailable ->
                        ReplyQuote(eid, "…", "Original unavailable", false)
                    is EmbeddedEventDetails.Error ->
                        ReplyQuote(eid, "…", ellipsize(embedded.message, 120), false)
                    else ->
                        ReplyQuote(eid, "…", "", false)
                }
            }
        }.getOrNull()
    }

    private fun previewBodyFromTimelineContent(content: TimelineItemContent, ownUserId: String): String {
        if (content !is TimelineItemContent.MsgLike) return "Message"
        return when (val kind = content.content.kind) {
            is MsgLikeKind.Message -> extractFromMessageContent(kind.content, ownUserId, forPreview = true).body
            is MsgLikeKind.Sticker -> kind.body.ifBlank { "Sticker" }
            is MsgLikeKind.UnableToDecrypt -> "Encrypted message"
            is MsgLikeKind.Poll -> "Poll"
            is MsgLikeKind.Redacted -> "Message removed"
            is MsgLikeKind.LiveLocation -> "Live location"
            is MsgLikeKind.Other -> "[${kind.eventType}]"
            else -> "Message"
        }
    }

    private fun extractPayloadFromMsgLike(
        msgLike: MsgLikeContent,
        ownUserId: String,
        item: EventTimelineItem,
    ): Payload? {
        return when (val kind = msgLike.kind) {
            is MsgLikeKind.Message -> {
                mergeMediaPayload(extractFromMessageContent(kind.content, ownUserId), recoverAudioFromItem(item))
            }
            is MsgLikeKind.Sticker -> {
                val src = encodeMediaSource(kind.source)
                val mime = kind.info?.mimetype
                Payload(
                    body = kind.body.ifBlank { "Sticker" },
                    kind = TimelineContentKind.Sticker,
                    mediaJson = src,
                    contentJson = src,
                    mime = mime,
                    animated = kind.info?.isAnimated == true || isAnimatedMime(mime),
                )
            }
            is MsgLikeKind.UnableToDecrypt ->
                recoverAudioFromItem(item) ?: Payload("🔒 Encrypted message", TimelineContentKind.Other)
            is MsgLikeKind.Poll -> Payload("📊 Poll", TimelineContentKind.Other)
            is MsgLikeKind.Redacted -> recoverAudioFromItem(item)
            is MsgLikeKind.Other -> when (kind.eventType) {
                is MessageLikeEventType.Voice,
                is MessageLikeEventType.Audio,
                -> recoverAudioFromItem(item)
                    ?: Payload("Voice message", TimelineContentKind.Audio, isVoice = true)
                is MessageLikeEventType.File -> recoverAudioFromItem(item)
                    ?: Payload("Attachment", TimelineContentKind.File)
                else -> recoverAudioFromItem(item)
                    ?: Payload("[${kind.eventType}]", TimelineContentKind.Other)
            }
            is MsgLikeKind.LiveLocation -> Payload("📍 Live location", TimelineContentKind.Other)
            else -> recoverAudioFromItem(item)
        }
    }

    private fun extractFromMessageContent(
        mc: MessageContent,
        ownUserId: String,
        forPreview: Boolean = false,
    ): Payload {
        val mentionsMe = mc.mentions?.let { m -> m.room || m.userIds.contains(ownUserId) } == true
        val mt = mc.msgType
        return when (mt) {
            is MessageType.Text -> Payload(
                normalizeMentions(mc.body, mt.content.formatted, forPreview),
                TimelineContentKind.Text,
                mentionsMe = mentionsMe,
            )
            is MessageType.Notice -> Payload(
                normalizeMentions(mc.body, mt.content.formatted, forPreview),
                TimelineContentKind.Text,
                mentionsMe = mentionsMe,
            )
            is MessageType.Emote -> Payload(
                normalizeMentions(mc.body, mt.content.formatted, forPreview),
                TimelineContentKind.Text,
                mentionsMe = mentionsMe,
            )
            is MessageType.Image -> {
                val img = mt.content
                val mime = img.info?.mimetype
                val animated = img.info?.isAnimated == true || isAnimatedMime(mime)
                val thumb = encodeMediaSource(img.info?.thumbnailSource) ?: encodeMediaSource(img.source)
                val full = encodeMediaSource(img.source)
                val label = when {
                    !img.caption.isNullOrBlank() -> img.caption!!.trim()
                    !img.filename.isNullOrBlank() -> img.filename!!.trim()
                    else -> "Photo"
                }
                Payload(
                    body = label,
                    kind = TimelineContentKind.Image,
                    mediaJson = thumb,
                    contentJson = full?.takeIf { f -> f != thumb },
                    mime = mime,
                    animated = animated,
                    mentionsMe = mentionsMe,
                    fileName = img.filename?.takeIf { it.isNotBlank() },
                    sizeBytes = img.info?.size?.toLong(),
                )
            }
            is MessageType.Video -> {
                val v = mt.content
                val thumb = encodeMediaSource(v.info?.thumbnailSource) ?: encodeMediaSource(v.source)
                val full = encodeMediaSource(v.source)
                val dur = v.info?.duration?.toMillis()
                val label = when {
                    !v.caption.isNullOrBlank() -> v.caption!!.trim()
                    !v.filename.isNullOrBlank() -> v.filename!!.trim()
                    else -> "Video"
                }
                Payload(
                    body = label,
                    kind = TimelineContentKind.Video,
                    mediaJson = thumb,
                    contentJson = full,
                    durationMs = dur,
                    mime = v.info?.mimetype,
                    mentionsMe = mentionsMe,
                    fileName = v.filename?.takeIf { it.isNotBlank() },
                    sizeBytes = v.info?.size?.toLong(),
                )
            }
            is MessageType.Audio -> {
                val a = mt.content
                val voice = a.voice != null
                val dur = a.info?.duration?.toMillis()
                val label = when {
                    !a.caption.isNullOrBlank() -> a.caption!!.trim()
                    !a.filename.isNullOrBlank() -> a.filename!!.trim()
                    else -> if (voice) "Voice message" else "Audio"
                }
                Payload(
                    body = label,
                    kind = TimelineContentKind.Audio,
                    mediaJson = encodeMediaSource(a.source),
                    durationMs = dur,
                    mime = a.info?.mimetype,
                    isVoice = voice,
                    mentionsMe = mentionsMe,
                    fileName = a.filename?.takeIf { it.isNotBlank() },
                    sizeBytes = a.info?.size?.toLong(),
                )
            }
            is MessageType.File -> {
                val f = mt.content
                val mime = f.info?.mimetype
                val name = f.filename?.takeIf { it.isNotBlank() }
                val audio = isAudioMime(mime) || looksLikeAudioName(name)
                val label = name ?: if (audio) "Audio" else "Attachment"
                Payload(
                    body = if (audio) label else "📎 $label",
                    kind = if (audio) TimelineContentKind.Audio else TimelineContentKind.File,
                    mediaJson = encodeMediaSource(f.source),
                    mime = mime,
                    mentionsMe = mentionsMe,
                    fileName = name,
                    sizeBytes = f.info?.size?.toLong(),
                    isVoice = audio && (name?.contains("voice", ignoreCase = true) == true),
                )
            }
            is MessageType.Other -> {
                val audio = mt.msgtype.contains("audio", ignoreCase = true) ||
                    mt.msgtype.contains("voice", ignoreCase = true)
                if (audio) {
                    Payload(
                        body = mt.body.ifBlank { "Voice message" },
                        kind = TimelineContentKind.Audio,
                        isVoice = mt.msgtype.contains("voice", ignoreCase = true),
                        mentionsMe = mentionsMe,
                    )
                } else {
                    Payload(mt.body.ifBlank { "[${mt.msgtype}]" }, TimelineContentKind.Other, mentionsMe = mentionsMe)
                }
            }
            is MessageType.Location -> Payload("📍 Location", TimelineContentKind.Other, mentionsMe = mentionsMe)
            is MessageType.Gallery -> Payload("🖼 Gallery", TimelineContentKind.Other, mentionsMe = mentionsMe)
            else -> Payload("[${mt::class.simpleName}]", TimelineContentKind.Other, mentionsMe = mentionsMe)
        }
    }

    private val markdownMentionLink = Regex("""\[([^\]]*)]\(https://matrix\.to/#/([^)\s]+)\)""")
    private val htmlMentionAnchor = Regex(
        """<a\s+[^>]*href="https://matrix\.to/#/([^"]+)"[^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val htmlTag = Regex("<[^>]*>")

    private fun userIdFromPermalink(raw: String): String? {
        val decoded = runCatching { URLDecoder.decode(raw.substringBefore('?'), "UTF-8") }.getOrDefault(raw)
        return decoded.takeIf { it.startsWith("@") && it.contains(':') }
    }

    private fun htmlText(html: String): String =
        htmlTag.replace(html, "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

    /**
     * Mentions arrive as markdown links in the plain body (our own and Element X messages) or as
     * `matrix.to` anchors in the HTML body (Element Web and others). Timeline bodies carry the
     * Matrix user id so the UI can show the room member's current name; previews carry `@name`.
     */
    private fun normalizeMentions(body: String, formatted: FormattedBody?, forPreview: Boolean): String {
        var out = markdownMentionLink.replace(body) { m ->
            val uid = userIdFromPermalink(m.groupValues[2]) ?: return@replace m.value
            if (forPreview) {
                val label = m.groupValues[1].trim()
                if (label.startsWith("@")) label else "@$label"
            } else {
                uid
            }
        }
        val html = formatted?.takeIf { it.format is MessageFormat.Html }?.body ?: return out
        for (m in htmlMentionAnchor.findAll(html)) {
            val uid = userIdFromPermalink(m.groupValues[1]) ?: continue
            val label = htmlText(m.groupValues[2])
            if (label.isBlank() || label == uid) continue
            val token = if (forPreview) (if (label.startsWith("@")) label else "@$label") else uid
            val at = if (label.startsWith("@")) label else "@$label"
            out = when {
                out.contains(at) -> out.replaceFirst(at, token)
                out.contains(label) -> out.replaceFirst(label, token)
                else -> out
            }
        }
        return out
    }

    private fun isAnimatedMime(mime: String?): Boolean {
        val m = mime?.lowercase().orEmpty()
        return m.contains("gif") ||
            m.contains("webp") ||
            m.contains("gzip") ||
            m.contains("lottie") ||
            m.contains("tgsticker")
    }

    private fun encodeMediaSource(source: MediaSource?): String? {
        if (source == null) return null
        // Do not destroy the timeline-owned source; later diffs remap the same item.
        return runCatching { source.toJson() }.getOrNull()
    }

    private fun mergeMediaPayload(mapped: Payload, media: Payload?): Payload {
        if (media == null) return mapped
        val keepBody = mapped.body.takeIf { body ->
            body.isNotBlank() &&
                !body.startsWith("[") &&
                !body.equals("Message removed", ignoreCase = true)
        }
        return when (mapped.kind) {
            TimelineContentKind.Text, TimelineContentKind.Other ->
                media.copy(body = keepBody ?: media.body)
            TimelineContentKind.Audio, TimelineContentKind.File, TimelineContentKind.Video ->
                mapped.copy(
                    mediaJson = mapped.mediaJson ?: media.mediaJson,
                    contentJson = mapped.contentJson ?: media.contentJson,
                    durationMs = mapped.durationMs ?: media.durationMs,
                    mime = mapped.mime ?: media.mime,
                    fileName = mapped.fileName ?: media.fileName,
                    sizeBytes = mapped.sizeBytes ?: media.sizeBytes,
                    isVoice = mapped.isVoice || media.isVoice,
                    kind = if (mapped.kind == TimelineContentKind.File && media.kind == TimelineContentKind.Audio) {
                        TimelineContentKind.Audio
                    } else {
                        mapped.kind
                    },
                )
            else -> mapped
        }
    }

    private fun recoverAudioFromItem(item: EventTimelineItem): Payload? {
        val blobs = listOf(
            runCatching { item.lazyProvider.latestJson() }.getOrNull(),
            runCatching { item.lazyProvider.debugInfo().latestEditJson }.getOrNull(),
            runCatching { item.lazyProvider.debugInfo().originalJson }.getOrNull(),
        )
        return blobs.firstNotNullOfOrNull { parseAudioPayload(it) }
    }

    /**
     * Builds a timeline row from a raw `/messages` or `/context` client event.
     * Used to stitch media the sliding-sync extras omitted from the local cache.
     */
    internal fun messageFromClientEvent(
        roomId: MatrixRoomId,
        ownUserId: String,
        event: JSONObject,
    ): TimelineMessage? {
        val eventId = event.optString("event_id").takeIf { it.isNotBlank() } ?: return null
        val sender = event.optString("sender").takeIf { it.isNotBlank() } ?: return null
        val ts = event.optLong("origin_server_ts").takeIf { it > 0 } ?: return null
        val content = event.optJSONObject("content") ?: JSONObject()
        val payload = parsePlayablePayload(event.optString("type"), content) ?: return null
        if (payload.mediaJson.isNullOrBlank()) return null
        return TimelineMessage(
            eventId = MatrixEventId(eventId),
            roomId = roomId,
            senderId = MatrixUserId(sender),
            senderDisplayName = sender.substringAfter("@").substringBefore(":").ifBlank { sender },
            senderAvatarMxc = null,
            body = payload.body,
            timestampMs = ts,
            isOwn = sender == ownUserId,
            contentKind = payload.kind,
            mediaSourceJson = payload.mediaJson,
            mediaContentJson = payload.contentJson,
            mediaDurationMs = payload.durationMs,
            mediaMimeType = payload.mime,
            isVoiceMessage = payload.isVoice,
            mediaFileName = payload.fileName,
            mediaSizeBytes = payload.sizeBytes,
        )
    }

    private fun parseAudioPayload(raw: String?): Payload? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            val content = obj.optJSONObject("content") ?: return@runCatching null
            parsePlayablePayload(obj.optString("type"), content)
        }.getOrNull()
    }

    private fun parsePlayablePayload(type: String, content: JSONObject): Payload? {
        val msgtype = content.optString("msgtype")
        val file = content.optJSONObject("file")
        val mscFile = content.optJSONObject("org.matrix.msc1767.file")
        val url = content.optString("url").ifBlank {
            file?.optString("url").orEmpty()
        }.ifBlank {
            mscFile?.optString("url").orEmpty()
        }
        val info = content.optJSONObject("info")
        val mime = info?.optString("mimetype").orEmpty()
            .ifBlank { mscFile?.optString("mimetype").orEmpty() }
        val filename = content.optString("filename").ifBlank {
            mscFile?.optString("name").orEmpty()
        }.takeIf { it.isNotBlank() }
        val body = content.optString("body")
        val voice = content.has("org.matrix.msc3245.voice") ||
            content.has("org.matrix.msc2516.voice") ||
            type.contains("voice", ignoreCase = true) ||
            msgtype.contains("voice", ignoreCase = true)
        val duration = info?.optLong("duration")?.takeIf { it > 0 }
            ?: content.optJSONObject("org.matrix.msc1767.audio")?.optLong("duration")?.takeIf { it > 0 }
        val video = msgtype == "m.video" || type == "m.video" || mime.startsWith("video/")
        val audio = !video && (
            voice ||
                isAudioMime(mime) ||
                msgtype == "m.audio" ||
                type == "m.audio" ||
                type.contains("voice", ignoreCase = true) ||
                looksLikeAudioName(filename ?: body) ||
                (msgtype == "m.file" && duration != null)
            )
        if (!audio && !video) return null
        val mediaJson = when {
            file != null && file.optString("url").isNotBlank() -> file.toString()
            url.isNotBlank() -> runCatching { MediaSource.fromUrl(url).use { it.toJson() } }.getOrNull()
                ?: """{"url":${JSONObject.quote(url)}}"""
            else -> null
        }
        val kind = if (audio) TimelineContentKind.Audio else TimelineContentKind.Video
        return Payload(
            body = when {
                body.isNotBlank() && !body.equals("Voice message", ignoreCase = true) -> body
                voice -> "Voice message"
                filename != null -> filename
                audio -> "Audio"
                else -> "Video"
            },
            kind = kind,
            mediaJson = mediaJson,
            contentJson = mediaJson,
            durationMs = duration,
            mime = mime.ifBlank { null },
            isVoice = voice,
            fileName = filename,
            sizeBytes = info?.optLong("size")?.takeIf { it > 0 },
        )
    }

    private fun isAudioMime(mime: String?): Boolean =
        mime?.lowercase()?.startsWith("audio/") == true

    private fun looksLikeAudioName(name: String?): Boolean {
        val n = name?.lowercase().orEmpty()
        return n.endsWith(".ogg") || n.endsWith(".mp3") || n.endsWith(".m4a") ||
            n.endsWith(".opus") || n.endsWith(".wav") || n.endsWith(".flac") ||
            n.contains("voice")
    }
}

package com.nextgenmatrix.matrix.timeline

import com.nextgenmatrix.core.model.MatrixEventId
import com.nextgenmatrix.core.model.MatrixRoomId
import com.nextgenmatrix.core.model.MatrixUserId
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
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.TimelineItemContent
import org.matrix.rustcomponents.sdk.use
import java.net.URLDecoder

internal object TimelineMessageMapper {
    fun mapItems(
        roomId: MatrixRoomId,
        ownUserId: String,
        items: List<TimelineItem>,
    ): List<TimelineMessage> =
        items.mapNotNull { item ->
            val eventItem = item.asEvent() ?: return@mapNotNull null
            mapEvent(roomId, ownUserId, eventItem)
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
        val payload = extractPayloadFromMsgLike(msgLike, ownUserId) ?: return null
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
        )
    }

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

    private fun extractPayloadFromMsgLike(msgLike: MsgLikeContent, ownUserId: String): Payload? {
        return when (val kind = msgLike.kind) {
            is MsgLikeKind.Message -> extractFromMessageContent(kind.content, ownUserId)
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
            is MsgLikeKind.UnableToDecrypt -> Payload("🔒 Encrypted message", TimelineContentKind.Other)
            is MsgLikeKind.Poll -> Payload("📊 Poll", TimelineContentKind.Other)
            is MsgLikeKind.Redacted -> Payload("Message removed", TimelineContentKind.Other)
            is MsgLikeKind.Other -> Payload("[${kind.eventType}]", TimelineContentKind.Other)
            is MsgLikeKind.LiveLocation -> Payload("📍 Live location", TimelineContentKind.Other)
            else -> null
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
                val label = when {
                    !f.filename.isNullOrBlank() -> f.filename!!.trim()
                    else -> "Attachment"
                }
                Payload(
                    body = "📎 $label",
                    kind = TimelineContentKind.File,
                    mediaJson = encodeMediaSource(f.source),
                    mime = f.info?.mimetype,
                    mentionsMe = mentionsMe,
                    fileName = f.filename?.takeIf { it.isNotBlank() },
                    sizeBytes = f.info?.size?.toLong(),
                )
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
        return runCatching {
            source.use { it.toJson() }
        }.getOrNull()
    }
}

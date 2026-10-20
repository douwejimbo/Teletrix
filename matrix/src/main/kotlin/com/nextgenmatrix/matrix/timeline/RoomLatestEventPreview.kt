package com.nextgenmatrix.matrix.timeline

import org.matrix.rustcomponents.sdk.LatestEventValue
import org.matrix.rustcomponents.sdk.MembershipChange
import org.matrix.rustcomponents.sdk.MsgLikeKind
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.TimelineItemContent

internal data class RoomLastMessageLine(
    val text: String,
    val timestampMs: Long,
)

/**
 * One-line preview for the room list from sliding-sync [org.matrix.rustcomponents.sdk.Room.latestEvent].
 */
internal object RoomLatestEventPreview {

    fun summarize(latest: LatestEventValue, ownUserId: String): RoomLastMessageLine? {
        return when (latest) {
            is LatestEventValue.Local -> buildLine(latest.timestamp.toLong(), latest.sender, latest.profile, latest.content, ownUserId)
            is LatestEventValue.Remote -> buildLine(latest.timestamp.toLong(), latest.sender, latest.profile, latest.content, ownUserId)
            else -> null
        }
    }

    private fun buildLine(
        timestamp: Long,
        sender: String,
        profile: ProfileDetails,
        content: TimelineItemContent,
        ownUserId: String,
    ): RoomLastMessageLine? {
        val inner = previewForTimelineContent(content, ownUserId) ?: return null
        val isOwn = sender == ownUserId
        val who = if (isOwn) "You" else displayNameFor(profile, sender)
        return RoomLastMessageLine(text = "$who: $inner", timestampMs = timestamp)
    }

    private fun displayNameFor(profile: ProfileDetails, sender: String): String {
        return when (profile) {
            is ProfileDetails.Ready ->
                profile.displayName?.takeIf { it.isNotBlank() } ?: shortMx(sender)
            else -> shortMx(sender)
        }
    }

    private fun shortMx(mxid: String): String =
        mxid.substringAfter("@").substringBefore(":").ifBlank { mxid }

    private fun previewForTimelineContent(content: TimelineItemContent, ownUserId: String): String? {
        return when (content) {
            is TimelineItemContent.MsgLike -> previewMsgLike(content.content, ownUserId)
            is TimelineItemContent.RoomMembership -> previewMembership(content)
            is TimelineItemContent.ProfileChange -> "Profile updated"
            is TimelineItemContent.State -> "Room settings updated"
            is TimelineItemContent.CallInvite -> "Call"
            is TimelineItemContent.RtcNotification -> "Call update"
            is TimelineItemContent.FailedToParseMessageLike -> "Unsupported message"
            is TimelineItemContent.FailedToParseState -> "Room update"
            else -> null
        }
    }

    private fun previewMembership(m: TimelineItemContent.RoomMembership): String? {
        val who = m.userDisplayName?.takeIf { it.isNotBlank() } ?: shortMx(m.userId)
        return when (m.change) {
            MembershipChange.JOINED -> "$who joined"
            MembershipChange.LEFT -> "$who left"
            MembershipChange.BANNED -> "$who was banned"
            MembershipChange.UNBANNED -> "$who was unbanned"
            MembershipChange.KICKED -> "$who was removed"
            MembershipChange.INVITED -> "$who was invited"
            MembershipChange.KICKED_AND_BANNED -> "$who was removed"
            MembershipChange.INVITATION_ACCEPTED -> "$who accepted invite"
            MembershipChange.INVITATION_REJECTED -> "$who declined invite"
            MembershipChange.INVITATION_REVOKED -> "Invite revoked"
            MembershipChange.KNOCKED -> "$who knocked"
            MembershipChange.KNOCK_ACCEPTED -> "$who was let in"
            MembershipChange.KNOCK_RETRACTED -> "$who retracted knock"
            MembershipChange.KNOCK_DENIED -> "Knock denied"
            MembershipChange.NONE, MembershipChange.ERROR, MembershipChange.NOT_IMPLEMENTED -> null
            null -> null
        }
    }

    private fun previewMsgLike(msgLike: org.matrix.rustcomponents.sdk.MsgLikeContent, ownUserId: String): String? {
        return when (val kind = msgLike.kind) {
            is MsgLikeKind.Message -> TimelineMessageMapper.messagePreviewBody(kind.content, ownUserId)
            is MsgLikeKind.Sticker -> kind.body.ifBlank { "Sticker" }
            is MsgLikeKind.UnableToDecrypt -> "Encrypted message"
            is MsgLikeKind.Poll -> "Poll"
            is MsgLikeKind.Redacted -> "Message removed"
            is MsgLikeKind.LiveLocation -> "Live location"
            is MsgLikeKind.Other -> "Message"
            else -> null
        }
    }
}

package com.nextgenmatrix.core.model

import kotlinx.serialization.Serializable

@JvmInline
value class MatrixUserId(val value: String)

@JvmInline
value class MatrixRoomId(val value: String)

@JvmInline
value class MatrixEventId(val value: String)

@Serializable
data class AccountSummary(
    val userId: String,
    val displayName: String?,
    val avatarMxc: String?,
    val homeserverUrl: String,
    val deviceId: String? = null,
)

/** A Matrix space, or a room listed inside one. */
data class SpaceEntry(
    val roomId: String,
    val title: String,
    val topic: String?,
    val avatarMxc: String?,
    val memberCount: Int,
    val childCount: Int,
    val isSpace: Boolean,
    val joined: Boolean,
    /** Servers that can be asked when joining a room this account is not in yet. */
    val via: List<String> = emptyList(),
    /** Why a tap will join or knock, when this account is not a member. */
    val joinNote: String? = null,
)

/** A room or space published in a homeserver's public room directory. */
data class DirectoryEntry(
    val roomId: String,
    val title: String,
    val topic: String?,
    val alias: String?,
    val avatarMxc: String?,
    val memberCount: Int,
    val joined: Boolean,
    val isSpace: Boolean,
    val joinNote: String?,
    val via: List<String> = emptyList(),
)

data class DirectoryPage(
    val entries: List<DirectoryEntry>,
    val hasMore: Boolean,
)

data class SpaceContents(
    val space: SpaceEntry?,
    val children: List<SpaceEntry>,
)

enum class RoomPresentationKind {
    DirectMessage,
    Group,
    Channel,
    Space,
}

data class RoomSummary(
    val roomId: MatrixRoomId,
    val title: String,
    val subtitle: String?,
    val avatarMxc: String?,
    val isEncrypted: Boolean,
    val unreadCount: Int,
    val mentionCount: Int = 0,
    val lastActivityTimestampMs: Long?,
    val presentationKind: RoomPresentationKind,
    val isSpace: Boolean = false,
)

enum class TimelineContentKind {
    Text,
    Image,
    Video,
    Audio,
    File,
    Sticker,
    Other,
}

data class TimelineMessage(
    val eventId: MatrixEventId,
    val roomId: MatrixRoomId,
    val senderId: MatrixUserId,
    val senderDisplayName: String?,
    val senderAvatarMxc: String?,
    val body: String,
    val timestampMs: Long,
    val isOwn: Boolean,
    val contentKind: TimelineContentKind = TimelineContentKind.Text,
    /** JSON for [org.matrix.rustcomponents.sdk.MediaSource.fromJson] — inline preview (image thumb, video poster, audio source, …). */
    val mediaSourceJson: String? = null,
    /** Full-resolution / playable media JSON for [getMediaContent]; falls back to [mediaSourceJson] when null. */
    val mediaContentJson: String? = null,
    val mediaDurationMs: Long? = null,
    val mediaMimeType: String? = null,
    /** True when the sender marked the image or sticker as animated (GIF, WebP, or TGS). */
    val isAnimatedMedia: Boolean = false,
    val isVoiceMessage: Boolean = false,
    val mentionsMe: Boolean = false,
    val transactionId: String? = null,
    val replyToEventId: MatrixEventId? = null,
    /** Short label for the quoted message author (e.g. display name, or "You" when the quoted message was yours). */
    val replyToPreviewAuthor: String? = null,
    /** One–two line preview of the message being replied to. */
    val replyToPreviewBody: String? = null,
    /** True when the reply target was sent by the current user (show "Replied to you"). */
    val replyToParentIsOwn: Boolean = false,
    /** Matrix user id of the quoted message author, used to resolve a display name from room members. */
    val replyToSenderId: String? = null,
    /** Original file name for file, audio and video attachments. */
    val mediaFileName: String? = null,
    val mediaSizeBytes: Long? = null,
    val threadRootEventId: MatrixEventId? = null,
    val isRedacted: Boolean = false,
    val replacesEventId: MatrixEventId? = null,
    /** Other users whose read receipt sits on this event. */
    val readByUserIds: List<String> = emptyList(),
    /** Other users whose read receipt points at this event (own outgoing bubbles). */
    val readReceiptOtherCount: Int = 0,
)

data class HomeserverLoginDetails(
    val supportsPasswordLogin: Boolean,
    val supportsRegistration: Boolean,
    val supportsOidc: Boolean,
    val supportsQrLogin: Boolean,
)

data class PusherConfig(
    val url: String,
    val appId: String,
    val deviceToken: String,
)

data class ModerationCapabilities(
    val canChangePowerLevels: Boolean,
    val canKick: Boolean,
    val canBan: Boolean,
    val canRedact: Boolean,
    val isAdmin: Boolean,
    val slowModeSeconds: Int?,
    val isAnnouncementsOnly: Boolean,
)

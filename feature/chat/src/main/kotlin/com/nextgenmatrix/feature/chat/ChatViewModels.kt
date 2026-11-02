package com.nextgenmatrix.feature.chat

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextgenmatrix.core.model.MatrixRoomId
import com.nextgenmatrix.core.model.MentionTag
import com.nextgenmatrix.core.model.RoomMemberBrief
import com.nextgenmatrix.core.model.TimelineContentKind
import com.nextgenmatrix.core.model.TimelineMessage
import com.nextgenmatrix.data.db.dao.ChatDraftDao
import com.nextgenmatrix.data.db.dao.ChatScrollStateDao
import com.nextgenmatrix.data.db.entity.ChatDraftEntity
import com.nextgenmatrix.data.db.entity.ChatScrollStateEntity
import com.nextgenmatrix.data.media.ChatMusicPlayer
import com.nextgenmatrix.data.media.MusicTrack
import com.nextgenmatrix.data.media.NowPlaying
import com.nextgenmatrix.matrix.MatrixRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Active reply target shown above the composer (plain event id, never `txn:`). */
data class PendingComposeReply(
    val eventId: String,
    val authorLabel: String,
    val bodyPreview: String,
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val matrixRepository: MatrixRepository,
) : ViewModel() {
    val rooms: StateFlow<List<com.nextgenmatrix.core.model.RoomSummary>> = matrixRepository.roomSummaries

    suspend fun loadRoomAvatar(mxc: String?): ByteArray? =
        matrixRepository.loadMatrixAvatarThumbnail(mxc)
}

@HiltViewModel
class ConversationViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val matrixRepository: MatrixRepository,
    private val chatDraftDao: ChatDraftDao,
    private val chatScrollStateDao: ChatScrollStateDao,
    private val musicPlayer: ChatMusicPlayer,
    private val attentionMarks: AttentionMarks,
) : ViewModel() {

    private val _roomId = MutableStateFlow<String?>(null)
    val roomId: StateFlow<String?> = _roomId.asStateFlow()

    val messages = matrixRepository.timelineMessages

    val roomMembersByRoom = matrixRepository.roomMembersByRoom

    val nowPlaying: StateFlow<NowPlaying?> = musicPlayer.nowPlaying

    private val _preparingTrackId = MutableStateFlow<String?>(null)
    val preparingTrackId: StateFlow<String?> = _preparingTrackId.asStateFlow()

    private val _scrollRestoreEventId = MutableStateFlow<String?>(null)
    val scrollRestoreEventId: StateFlow<String?> = _scrollRestoreEventId.asStateFlow()

    /** Incremented after each outbound send so the UI can scroll to the newest row. */
    private val _scrollToLatestSignal = MutableStateFlow(0)
    val scrollToLatestSignal: StateFlow<Int> = _scrollToLatestSignal.asStateFlow()

    private val _jumpToEventId = MutableStateFlow<String?>(null)
    val jumpToEventId: StateFlow<String?> = _jumpToEventId.asStateFlow()

    private val _pendingReply = MutableStateFlow<PendingComposeReply?>(null)
    val pendingReply: StateFlow<PendingComposeReply?> = _pendingReply.asStateFlow()

    private val _mentionWatermarkMs = MutableStateFlow(-1L)
    val mentionWatermarkMs: StateFlow<Long> = _mentionWatermarkMs.asStateFlow()

    private val _reactionWatermarkMs = MutableStateFlow(-1L)
    val reactionWatermarkMs: StateFlow<Long> = _reactionWatermarkMs.asStateFlow()

    private var attentionJob: Job? = null

    fun setComposeReply(message: TimelineMessage) {
        val eid = message.eventId.value
        if (eid.startsWith("txn:")) return
        val memberById = _roomId.value?.let { roomMembersByRoom.value[it] }.orEmpty().associateBy { it.userId }
        val author =
            if (message.isOwn) {
                "You"
            } else {
                displayNameFor(message.senderId.value, message.senderDisplayName, memberById)
            }
        val preview = mentionsToLabels(message.body, memberById)
            .replace("\n", " ").trim().take(140).ifBlank { "Message" }
        _pendingReply.value = PendingComposeReply(eventId = eid, authorLabel = author, bodyPreview = preview)
    }

    fun clearComposeReply() {
        _pendingReply.value = null
    }

    fun bindRoom(id: String) {
        val roomChanged = _roomId.value != id
        _roomId.value = id
        matrixRepository.setActiveConversation(id)
        if (roomChanged) {
            _pendingReply.value = null
            _jumpToEventId.value = null
            pickedMentions.clear()
        }
        viewModelScope.launch {
            _scrollRestoreEventId.value = chatScrollStateDao.get(id)?.anchorEventId
            if (roomChanged) {
                matrixRepository.observeTimeline(MatrixRoomId(id))
            }
        }
        val hasUnreadMentions = matrixRepository.roomSummaries.value
            .firstOrNull { it.roomId.value == id }
            ?.mentionCount
            ?.let { it > 0 } == true
        attentionMarks.ensureMentionWatermark(id, hasUnreadMentions)
        attentionMarks.ensureReactionWatermark(id)
        _mentionWatermarkMs.value = attentionMarks.mentionWatermark(id)
        _reactionWatermarkMs.value = attentionMarks.reactionWatermark(id)
        attentionJob?.cancel()
        attentionJob = viewModelScope.launch {
            messages.collect { map ->
                refineMentionWatermark(id, map[id].orEmpty())
            }
        }
    }

    private fun refineMentionWatermark(roomId: String, list: List<TimelineMessage>) {
        if (_mentionWatermarkMs.value != 0L) return
        val count = matrixRepository.roomSummaries.value
            .firstOrNull { it.roomId.value == roomId }
            ?.mentionCount ?: 0
        if (count <= 0) {
            markMentionRead(System.currentTimeMillis())
            return
        }
        val mentions = list.filter { it.mentionsMe && !it.isOwn }
            .sortedByDescending { it.timestampMs }
        val oldestUnread = mentions.take(count).minOfOrNull { it.timestampMs } ?: return
        _mentionWatermarkMs.value = attentionMarks.raiseMentionWatermark(roomId, oldestUnread - 1)
    }

    fun markMentionRead(timestampMs: Long) {
        val room = _roomId.value ?: return
        _mentionWatermarkMs.value = attentionMarks.raiseMentionWatermark(room, timestampMs)
    }

    fun markReactionRead(timestampMs: Long) {
        val room = _roomId.value ?: return
        _reactionWatermarkMs.value = attentionMarks.raiseReactionWatermark(room, timestampMs)
    }

    override fun onCleared() {
        matrixRepository.setActiveConversation(null)
        super.onCleared()
    }

    fun jumpToReply(eventId: String) {
        val target = eventId.trim()
        if (target.isEmpty() || target.startsWith("txn:")) return
        val room = _roomId.value ?: return
        viewModelScope.launch {
            if (matrixRepository.ensureEventInTimeline(MatrixRoomId(room), target)) {
                _jumpToEventId.value = target
            }
        }
    }

    fun consumeJumpToEvent() {
        _jumpToEventId.value = null
    }

    fun consumeScrollRestore() {
        _scrollRestoreEventId.value = null
    }

    fun persistScrollAnchor(roomId: String, anchorEventId: String) {
        viewModelScope.launch {
            chatScrollStateDao.upsert(
                ChatScrollStateEntity(
                    roomId = roomId,
                    anchorEventId = anchorEventId,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** People picked from the @ list while writing; cleared on send and when the room changes. */
    private val pickedMentions = LinkedHashMap<String, RoomMemberBrief>()

    fun onMentionPicked(member: RoomMemberBrief) {
        pickedMentions[member.userId] = member
    }

    fun send(roomId: String, body: String) {
        val members = roomMembersByRoom.value[roomId].orEmpty()
        val tags = resolveMentionTags(body, members, pickedMentions.values.toList())
        pickedMentions.clear()
        viewModelScope.launch {
            val pending = _pendingReply.value
            if (pending != null) {
                matrixRepository.sendReply(MatrixRoomId(roomId), pending.eventId, body, tags)
                _pendingReply.value = null
            } else {
                matrixRepository.sendText(MatrixRoomId(roomId), body, tags)
            }
            _scrollToLatestSignal.value = _scrollToLatestSignal.value + 1
        }
    }

    fun sendAttachment(roomId: String, bytes: ByteArray, fileName: String, mimeType: String) {
        viewModelScope.launch {
            _pendingReply.value = null
            matrixRepository.sendAttachment(MatrixRoomId(roomId), bytes, fileName, mimeType)
            _scrollToLatestSignal.value = _scrollToLatestSignal.value + 1
        }
    }

    fun deleteMessage(message: TimelineMessage) {
        val room = _roomId.value ?: return
        val eventId = message.eventId.value.trim()
        if (eventId.isEmpty() || !message.isOwn) return
        viewModelScope.launch {
            matrixRepository.redactMessage(MatrixRoomId(room), eventId)
        }
    }

    fun sendSticker(roomId: String, bytes: ByteArray, fileName: String, mimeType: String) {
        viewModelScope.launch {
            _pendingReply.value = null
            matrixRepository.sendSticker(MatrixRoomId(roomId), bytes, fileName, mimeType)
            _scrollToLatestSignal.value = _scrollToLatestSignal.value + 1
        }
    }

    /** The viewer plays its own audio, so the shared music player steps aside. */
    fun pauseMusicForViewer() {
        if (musicPlayer.nowPlaying.value?.isPlaying == true) musicPlayer.toggle()
    }

    fun onPlayChatAudio(message: TimelineMessage) {
        if (message.contentKind != TimelineContentKind.Audio) return
        val now = musicPlayer.nowPlaying.value
        if (now?.trackId == message.eventId.value) {
            musicPlayer.toggle()
            return
        }
        val roomId = _roomId.value ?: return
        val snapshot = messages.value[roomId].orEmpty()
        viewModelScope.launch {
            _preparingTrackId.value = message.eventId.value
            val selected = withContext(Dispatchers.IO) { loadTrack(message) }
            if (selected == null) {
                _preparingTrackId.value = null
                return@launch
            }
            musicPlayer.play(listOf(selected), 0)
            _preparingTrackId.value = null
            val voice = message.isVoiceMessage
            val audio = snapshot.filter {
                it.contentKind == TimelineContentKind.Audio && it.isVoiceMessage == voice
            }
            val tracks = withContext(Dispatchers.IO) {
                audio.mapNotNull { msg ->
                    if (msg.eventId.value == message.eventId.value) selected else loadTrack(msg)
                }
            }
            if (tracks.size > 1) {
                musicPlayer.replaceQueue(tracks, message.eventId.value)
            }
        }
    }

    private suspend fun loadTrack(message: TimelineMessage): MusicTrack? {
        val json = message.mediaContentJson ?: message.mediaSourceJson ?: return null
        val bytes = matrixRepository.loadMatrixMediaContent(json) ?: return null
        if (bytes.isEmpty()) return null
        val safe = message.eventId.value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        val file = File(appContext.cacheDir, "music/$safe.${audioExtension(bytes)}")
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        val roomTitle = _roomId.value?.let { id ->
            matrixRepository.roomSummaries.value.firstOrNull { it.roomId.value == id }?.title
        }
        return MusicTrack(
            id = message.eventId.value,
            title = if (message.isVoiceMessage) {
                "Voice message"
            } else {
                message.mediaFileName?.takeIf { it.isNotBlank() } ?: message.body.ifBlank { "Audio" }
            },
            uri = Uri.fromFile(file),
            subtitle = roomTitle,
            isVoice = message.isVoiceMessage,
        )
    }

    fun updateRoomReadReceipts(roomId: String, lastReadEventId: String) {
        viewModelScope.launch {
            matrixRepository.updateRoomReadReceipts(MatrixRoomId(roomId), lastReadEventId)
        }
    }

    fun paginate(roomId: String) {
        viewModelScope.launch {
            matrixRepository.paginateBack(MatrixRoomId(roomId))
        }
    }

    fun loadDraft(roomId: String) {
        viewModelScope.launch {
            val d = chatDraftDao.get(roomId)?.body.orEmpty()
            _draft.value = d
        }
    }

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private var lastBackPaginationAt = 0L

    fun onDraftChange(v: String) {
        _draft.value = v
    }

    /** Throttled: called when user scrolls toward older messages (top of reversed list). */
    fun requestOlderMessagesIfNeeded(roomId: String, lastVisibleIndex: Int, totalCount: Int) {
        if (totalCount == 0 || lastVisibleIndex < totalCount - 3) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackPaginationAt < 1400L) return
        lastBackPaginationAt = now
        paginate(roomId)
    }

    fun persistDraft(roomId: String) {
        viewModelScope.launch {
            chatDraftDao.upsert(
                ChatDraftEntity(roomId = roomId, body = _draft.value, updatedAtMs = System.currentTimeMillis()),
            )
        }
    }

    suspend fun loadMediaThumbnail(sourceJson: String): ByteArray? =
        matrixRepository.loadMatrixMediaThumbnail(sourceJson)

    suspend fun loadMediaContent(sourceJson: String): ByteArray? =
        matrixRepository.loadMatrixMediaContent(sourceJson)

    suspend fun loadSenderAvatar(mxc: String?): ByteArray? =
        matrixRepository.loadMatrixAvatarThumbnail(mxc)

    private fun audioExtension(bytes: ByteArray): String = when {
        bytes.size >= 3 &&
            bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() &&
            bytes[2] == '3'.code.toByte() -> "mp3"
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0 -> "mp3"
        bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("OggS".toByteArray()) -> "ogg"
        bytes.size >= 12 && bytes.copyOfRange(4, 8).contentEquals("ftyp".toByteArray()) -> "m4a"
        bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) -> "wav"
        bytes.size >= 4 && bytes.copyOfRange(0, 4).contentEquals("fLaC".toByteArray()) -> "flac"
        else -> "mp3"
    }
}

/** Best name to show for a person: room member name, then the profile name on the event, then the username. */
internal fun displayNameFor(
    userId: String,
    eventProfileName: String?,
    memberById: Map<String, RoomMemberBrief>,
): String =
    memberById[userId]?.displayName?.takeIf { it.isNotBlank() }
        ?: eventProfileName?.takeIf { it.isNotBlank() }
        ?: userId.substringAfter("@").substringBefore(":").ifBlank { userId }

/**
 * Tags still present in [body]. Names typed by hand count too, so a restored draft keeps its
 * mentions. Picked people win when two members share a name.
 */
internal fun resolveMentionTags(
    body: String,
    members: List<RoomMemberBrief>,
    picked: List<RoomMemberBrief>,
): List<MentionTag> {
    fun present(label: String): Boolean {
        if (label.isBlank()) return false
        val token = "@$label"
        var from = 0
        while (true) {
            val at = body.indexOf(token, from, ignoreCase = false)
            if (at < 0) return false
            val before = body.getOrNull(at - 1)
            val after = body.getOrNull(at + token.length)
            val startOk = before == null || before.isWhitespace() || before in "([{\"'"
            val endOk = after == null || !(after.isLetterOrDigit() || after == '_')
            if (startOk && endOk) return true
            from = at + 1
        }
    }
    val out = LinkedHashMap<String, MentionTag>()
    fun labelOf(m: RoomMemberBrief) =
        m.displayName.ifBlank { m.userId.substringAfter('@').substringBefore(':') }
    for (m in picked) {
        val label = labelOf(m)
        if (present(label)) out[m.userId] = MentionTag(m.userId, label)
    }
    val takenLabels = out.values.map { it.label }.toMutableSet()
    for (m in members.sortedByDescending { labelOf(it).length }) {
        if (m.userId in out) continue
        val label = labelOf(m)
        if (label in takenLabels || label.length < 2) continue
        if (present(label)) {
            out[m.userId] = MentionTag(m.userId, label)
            takenLabels += label
        }
    }
    return out.values.toList()
}

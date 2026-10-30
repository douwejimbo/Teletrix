package com.nextgenmatrix.matrix

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import com.nextgenmatrix.core.common.AppDispatchers
import com.nextgenmatrix.core.model.AccountSummary
import com.nextgenmatrix.core.model.DirectoryEntry
import com.nextgenmatrix.core.model.DirectoryPage
import com.nextgenmatrix.core.model.HomeserverLoginDetails
import com.nextgenmatrix.core.model.ChatNotifyMode
import com.nextgenmatrix.core.model.IncomingNotification
import com.nextgenmatrix.core.model.NotificationPrefs
import com.nextgenmatrix.core.model.MatrixRoomId
import com.nextgenmatrix.core.model.MentionTag
import com.nextgenmatrix.core.model.RoomMemberBrief
import com.nextgenmatrix.core.model.RoomPresentationKind
import com.nextgenmatrix.core.model.RoomSummary
import com.nextgenmatrix.core.model.SpaceContents
import com.nextgenmatrix.core.model.SpaceEntry
import com.nextgenmatrix.core.model.SessionVerificationPhase
import com.nextgenmatrix.core.model.SessionVerificationPrompt
import com.nextgenmatrix.core.model.TimelineMessage
import com.nextgenmatrix.core.model.VerificationEmoji
import com.nextgenmatrix.matrix.delegate.NgMatrixClientDelegate
import com.nextgenmatrix.matrix.session.EncryptedMatrixSessionStore
import com.nextgenmatrix.matrix.timeline.RoomLatestEventPreview
import com.nextgenmatrix.matrix.timeline.TimelineDiffApplier
import com.nextgenmatrix.matrix.timeline.TimelineMessageMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.CrossProcessLockConfig
import org.matrix.rustcomponents.sdk.DateDividerMode
import org.matrix.rustcomponents.sdk.FileInfo
import org.matrix.rustcomponents.sdk.ImageInfo
import org.matrix.rustcomponents.sdk.JoinRule
import org.matrix.rustcomponents.sdk.Membership
import org.matrix.rustcomponents.sdk.MembershipState
import org.matrix.rustcomponents.sdk.Mentions
import org.matrix.rustcomponents.sdk.RoomType
import org.matrix.rustcomponents.sdk.TimelineConfiguration
import org.matrix.rustcomponents.sdk.TimelineFilter
import org.matrix.rustcomponents.sdk.TimelineFocus
import org.matrix.rustcomponents.sdk.SpaceRoom
import uniffi.matrix_sdk_ui.SpaceRoomListPaginationState
import uniffi.matrix_sdk_ui.TimelineReadReceiptTracking
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.matrix.rustcomponents.sdk.MediaSource
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomMember
import org.matrix.rustcomponents.sdk.RoomMembersIterator
import org.matrix.rustcomponents.sdk.RoomList
import org.matrix.rustcomponents.sdk.RoomListEntriesListener
import org.matrix.rustcomponents.sdk.RoomListEntriesUpdate
import org.matrix.rustcomponents.sdk.RoomListEntriesWithDynamicAdaptersResult
import org.matrix.rustcomponents.sdk.SendAttachmentJoinHandle
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SessionVerificationController
import org.matrix.rustcomponents.sdk.SessionVerificationControllerDelegate
import org.matrix.rustcomponents.sdk.SessionVerificationData
import org.matrix.rustcomponents.sdk.VerificationState
import org.matrix.rustcomponents.sdk.SessionVerificationRequestDetails
import org.matrix.rustcomponents.sdk.SlidingSyncVersion
import org.matrix.rustcomponents.sdk.RequestConfig
import org.matrix.rustcomponents.sdk.ProgressWatcher
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.SlidingSyncVersionBuilder
import org.matrix.rustcomponents.sdk.SqliteStoreBuilder as SdkSqliteStoreBuilder
import org.matrix.rustcomponents.sdk.Action
import org.matrix.rustcomponents.sdk.HttpPusherData
import org.matrix.rustcomponents.sdk.PushCondition
import org.matrix.rustcomponents.sdk.RoomNotificationMode
import org.matrix.rustcomponents.sdk.RuleKind
import org.matrix.rustcomponents.sdk.NotificationEvent
import org.matrix.rustcomponents.sdk.NotificationItem
import org.matrix.rustcomponents.sdk.NotificationProcessSetup
import org.matrix.rustcomponents.sdk.NotificationStatus
import org.matrix.rustcomponents.sdk.PushFormat
import org.matrix.rustcomponents.sdk.PusherIdentifiers
import org.matrix.rustcomponents.sdk.PusherKind
import org.matrix.rustcomponents.sdk.SyncNotificationListener
import org.matrix.rustcomponents.sdk.Timeline
import org.matrix.rustcomponents.sdk.TimelineDiff
import org.matrix.rustcomponents.sdk.TimelineItem
import org.matrix.rustcomponents.sdk.TimelineListener
import org.matrix.rustcomponents.sdk.messageEventContentFromMarkdown
import org.matrix.rustcomponents.sdk.UploadParameters
import org.matrix.rustcomponents.sdk.UploadSource
import org.matrix.rustcomponents.sdk.use
import uniffi.matrix_sdk_base.EncryptionState
import uniffi.matrix_sdk_crypto.CollectStrategy
import uniffi.matrix_sdk_crypto.DecryptionSettings
import uniffi.matrix_sdk_crypto.TrustRequirement
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import org.matrix.rustcomponents.sdk.SyncService as ClientSyncService

interface MatrixRepository {
    val isLoggedIn: StateFlow<Boolean>
    val account: StateFlow<AccountSummary?>
    val roomSummaries: StateFlow<List<RoomSummary>>
    val timelineMessages: StateFlow<Map<String, List<TimelineMessage>>>

    suspend fun setHomeserver(serverOrName: String): Result<HomeserverLoginDetails>
    suspend fun login(username: String, password: String): Result<Unit>
    suspend fun restoreSession(): Result<Unit>

    /** True when a previous login was saved and can be restored after a relaunch. */
    fun hasPersistedSession(): Boolean

    suspend fun logout()

    /** Room id → joined members (for @ autocomplete and mention pills). */
    val roomMembersByRoom: StateFlow<Map<String, List<RoomMemberBrief>>>
    /** Cross-device session verification (emoji / SAS). Null when nothing to show. */
    val sessionVerificationPrompt: StateFlow<SessionVerificationPrompt?>

    suspend fun observeTimeline(roomId: MatrixRoomId)
    suspend fun sendText(roomId: MatrixRoomId, body: String, mentions: List<MentionTag> = emptyList())
    /** Sends `m.room.message` with `m.in_reply_to` referencing [replyToEventId] (plain Matrix event id). */
    suspend fun sendReply(
        roomId: MatrixRoomId,
        replyToEventId: String,
        body: String,
        mentions: List<MentionTag> = emptyList(),
    )
    suspend fun sendAttachment(roomId: MatrixRoomId, bytes: ByteArray, fileName: String, mimeType: String)
    /** Uploads bytes and sends them as an `m.sticker` event. */
    suspend fun sendSticker(roomId: MatrixRoomId, bytes: ByteArray, fileName: String, mimeType: String)
    suspend fun paginateBack(roomId: MatrixRoomId): Boolean

    /**
     * Loads older pages until [eventId] is in the open timeline, or history runs out.
     * The conversation must already be observing [roomId].
     */
    suspend fun ensureEventInTimeline(roomId: MatrixRoomId, eventId: String): Boolean

    val incomingNotifications: SharedFlow<IncomingNotification>

    /** Room the user is looking at; notifications for it are suppressed. */
    fun setActiveConversation(roomId: String?)

    suspend fun registerHttpPusher(pushkey: String)

    suspend fun unregisterHttpPusher()

    /** Fetch and decrypt one event for a push payload. Restores the session if needed. */
    suspend fun notificationForEvent(roomId: String, eventId: String): IncomingNotification?

    fun setNotificationPrefs(prefs: NotificationPrefs)

    suspend fun applyNotificationPrefs()

    suspend fun approveSessionVerification()
    suspend fun declineSessionVerification()
    suspend fun cancelSessionVerification()
    /** Hide the verification sheet without affecting an in-flight protocol (clears UI state only). */
    suspend fun dismissSessionVerificationPrompt()

    /** After "Continue" on the pending step: advance SAS if the SDK is waiting for [startSasVerification]. */
    suspend fun continueSessionVerificationFromPending()

    /** Ask another signed-in device to verify this session with emoji SAS. */
    suspend fun requestSessionVerification()

    /** Public read receipt and fully-read marker for [lastReadEventId], the newest event actually on screen. */
    suspend fun updateRoomReadReceipts(roomId: MatrixRoomId, lastReadEventId: String)

    /** Matrix-authenticated thumbnail (images, video posters, room avatars, …). */
    suspend fun loadMatrixMediaThumbnail(sourceJson: String, maxWidth: Long = 900, maxHeight: Long = 900): ByteArray?

    /** Full decrypted media bytes (e.g. voice, HD image). */
    suspend fun loadMatrixMediaContent(sourceJson: String): ByteArray?

    /** Room or profile avatar from an `mxc://` URI (authenticated thumbnail fetch). */
    suspend fun loadMatrixAvatarThumbnail(mxcUri: String?, maxWidth: Long = 128, maxHeight: Long = 128): ByteArray?

    /** Reloads the signed-in user's name and avatar. */
    suspend fun refreshAccount()

    /** Spaces this account has joined that are not nested inside another joined space. */
    suspend fun topLevelSpaces(): List<SpaceEntry>

    /** The space itself, plus every room and space it lists, joined or not. */
    suspend fun spaceContents(spaceId: String): SpaceContents

    /**
     * Joins [roomId]. Returns whether the joined room is a space.
     * [via] is the list of servers that know the room, used when this account is not a member yet.
     */
    suspend fun joinListedRoom(roomId: String, via: List<String>): Result<Boolean>

    /** First page of this homeserver's published room directory. */
    suspend fun searchPublicRooms(query: String): DirectoryPage

    /** The next page of the directory search started by [searchPublicRooms]. */
    suspend fun morePublicRooms(): DirectoryPage
}

@Singleton
class DefaultMatrixRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val dispatchers: AppDispatchers,
    private val sessionStore: EncryptedMatrixSessionStore,
    private val delegate: NgMatrixClientDelegate,
) : MatrixRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val mutex = Mutex()

    private var client: Client? = null
    private var syncService: ClientSyncService? = null
    private var sessionPaths: SessionPaths? = null
    private var timelineHandle: org.matrix.rustcomponents.sdk.TaskHandle? = null
    private var activeTimeline: Timeline? = null
    private var activeTimelineRoomId: String? = null
    private val timelineItems = mutableListOf<TimelineItem>()

    private val directoryMutex = Mutex()
    private var directoryNextBatch: String? = null
    private var directoryQuery: String? = null
    private val directoryRooms = mutableListOf<DirectoryEntry>()

    private var roomList: RoomList? = null
    private var roomListAdapterResult: RoomListEntriesWithDynamicAdaptersResult? = null
    private var roomListStreamHandle: org.matrix.rustcomponents.sdk.TaskHandle? = null

    private val _isLoggedIn = MutableStateFlow(false)
    override val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _account = MutableStateFlow<AccountSummary?>(null)
    override val account: StateFlow<AccountSummary?> = _account.asStateFlow()

    private val _rooms = MutableStateFlow<List<RoomSummary>>(emptyList())

    /**
     * The room list reloads one event at a time, so the SDK count falls back to 1
     * whenever that window is rebuilt. [unreadHolds] keeps the highest count seen
     * since the room was last read here.
     */
    private val unreadHolds = java.util.concurrent.ConcurrentHashMap<String, UnreadHold>()
    override val roomSummaries: StateFlow<List<RoomSummary>> = _rooms.asStateFlow()

    private val _timeline = MutableStateFlow<Map<String, List<TimelineMessage>>>(emptyMap())
    override val timelineMessages: StateFlow<Map<String, List<TimelineMessage>>> = _timeline.asStateFlow()

    /** Avatars repeat on every message row, so decoded thumbnails bytes are kept in memory. */
    private val avatarCache = object : android.util.LruCache<String, ByteArray>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    private val _roomMembers = MutableStateFlow<Map<String, List<RoomMemberBrief>>>(emptyMap())
    override val roomMembersByRoom: StateFlow<Map<String, List<RoomMemberBrief>>> = _roomMembers.asStateFlow()

    private val _sessionVerificationPrompt = MutableStateFlow<SessionVerificationPrompt?>(null)
    override val sessionVerificationPrompt: StateFlow<SessionVerificationPrompt?> =
        _sessionVerificationPrompt.asStateFlow()

    private var roomPollJob: Job? = null

    private val _incomingNotifications = MutableSharedFlow<IncomingNotification>(extraBufferCapacity = 64)
    override val incomingNotifications: SharedFlow<IncomingNotification> = _incomingNotifications.asSharedFlow()

    @Volatile
    private var activeConversationRoomId: String? = null

    @Volatile
    private var registeredPushkey: String? = null

    @Volatile
    private var notificationPrefs: NotificationPrefs = NotificationPrefs()

    private val pusherAppId = "com.nextgenmatrix.app.android"

    @Volatile
    private var sessionVerificationController: SessionVerificationController? = null

    /** True once SAS has started. A later startSas() failure must not tear the flow down. */
    private val sasStarted = AtomicBoolean(false)

    /**
     * True when this device called requestDeviceVerification.
     * SAS itself is started by whichever side sees the request become ready first.
     * The other client only shows the emoji check after it receives that start.
     */
    private val outgoingVerification = AtomicBoolean(false)

    /** Ready: the other device accepted an outgoing request, or we accepted an incoming one. */
    private val requestAccepted = AtomicBoolean(false)

    /** Set once the user taps Match, so a repeat emoji callback cannot wipe that tap. */
    private val userConfirmedMatch = AtomicBoolean(false)

    private val approveInFlight = AtomicBoolean(false)
    private val approveGeneration = AtomicInteger(0)
    private val sasStartInFlight = AtomicBoolean(false)

    /** Ignore a cancel caused by clearing a stuck flow before a new request is sent. */
    private val suppressCancelUntilMs = AtomicLong(0)

    /** Incoming request that still needs acceptVerificationRequest(). Null for outbound. */
    @Volatile
    private var pendingIncomingRequest: Pair<String, String>? = null

    private val verificationDelegate =
        object : SessionVerificationControllerDelegate {
            override fun didReceiveVerificationRequest(details: SessionVerificationRequestDetails) {
                val userId = details.senderProfile.userId
                val flowId = details.flowId
                Log.i(VERIFY_LOG, "incoming request flow=$flowId user=$userId")
                pendingIncomingRequest = userId to flowId
                outgoingVerification.set(false)
                requestAccepted.set(false)
                sasStarted.set(false)
                userConfirmedMatch.set(false)
                scope.launch(dispatchers.io) {
                    val ctrl = sessionVerificationController ?: return@launch
                    runCatching { ctrl.acknowledgeVerificationRequest(userId, flowId) }
                        .onFailure { Log.e(VERIFY_LOG, "acknowledge failed", it) }
                }
                val label =
                    details.deviceDisplayName?.takeIf { it.isNotBlank() }
                        ?: details.deviceId?.takeIf { it.isNotBlank() }
                        ?: "another device"
                _sessionVerificationPrompt.value =
                    SessionVerificationPrompt(
                        phase = SessionVerificationPhase.Pending,
                        message = "Verification request from $label. Tap Continue to begin emoji verification.",
                    )
            }

            override fun didAcceptVerificationRequest() {
                Log.i(VERIFY_LOG, "request accepted outgoing=${outgoingVerification.get()}")
                requestAccepted.set(true)
                // Element and most clients send m.key.verification.start themselves a moment
                // after ready. If we also send one, both starts cross: the SDK keeps one on
                // each side, the emoji differ, and the other client cancels with
                // m.mismatched_sas. So wait for their start, and only start ourselves
                // (Element X style peers) if nothing arrived. The callback runs under the
                // SDK delegate lock, so this must be scheduled.
                scope.launch(dispatchers.io) {
                    delay(SAS_START_GRACE_MS)
                    if (!sasStarted.get() &&
                        _sessionVerificationPrompt.value?.phase != SessionVerificationPhase.SasCompare
                    ) {
                        Log.i(VERIFY_LOG, "no start from the other device, starting SAS ourselves")
                        startSasOnce()
                    }
                }
                if (_sessionVerificationPrompt.value?.phase != SessionVerificationPhase.SasCompare) {
                    _sessionVerificationPrompt.value =
                        SessionVerificationPrompt(
                            phase = SessionVerificationPhase.Pending,
                            message = if (outgoingVerification.get()) {
                                "Other device accepted. Starting the emoji check."
                            } else {
                                "Accepted. The emoji check will open on both devices."
                            },
                        )
                }
            }

            override fun didStartSasVerification() {
                Log.i(VERIFY_LOG, "sas started")
                sasStarted.set(true)
            }

            override fun didReceiveVerificationData(data: SessionVerificationData) {
                when (data) {
                    is SessionVerificationData.Emojis -> {
                        val emojis = ArrayList<VerificationEmoji>(data.emojis.size)
                        for (e in data.emojis) {
                            val symbol = runCatching { e.symbol() }.getOrDefault("")
                            val description = runCatching { e.description() }.getOrDefault("")
                            if (symbol.isNotEmpty()) {
                                emojis += VerificationEmoji(symbol, description)
                            }
                            runCatching { e.close() }
                        }
                        Log.i(VERIFY_LOG, "emoji challenge count=${emojis.size}")
                        if (userConfirmedMatch.get() || approveInFlight.get()) {
                            Log.i(VERIFY_LOG, "emoji update ignored after Match")
                            return
                        }
                        _sessionVerificationPrompt.value =
                            SessionVerificationPrompt(
                                phase = SessionVerificationPhase.SasCompare,
                                emojis = emojis,
                                message = "Confirm the same emoji order on your other signed-in Matrix client, then tap Match.",
                            )
                    }
                    is SessionVerificationData.Decimals -> {
                        val nums = data.values.joinToString(" ") { it.toString() }
                        _sessionVerificationPrompt.value =
                            SessionVerificationPrompt(
                                phase = SessionVerificationPhase.SasCompare,
                                message = "Compare numbers on your other device: $nums",
                            )
                    }
                    else -> {
                        runCatching { data.destroy() }
                    }
                }
            }

            override fun didFail() {
                Log.w(
                    VERIFY_LOG,
                    "didFail sasStarted=${sasStarted.get()} phase=${_sessionVerificationPrompt.value?.phase}",
                )
                // startSas() reports failure when the other device already started SAS.
                // Closing the controller here aborts the live check and the other client times out.
                if (sasStarted.get() ||
                    _sessionVerificationPrompt.value?.phase == SessionVerificationPhase.SasCompare
                ) {
                    return
                }
                sasStarted.set(false)
                requestAccepted.set(false)
                userConfirmedMatch.set(false)
                pendingIncomingRequest = null
                _sessionVerificationPrompt.value =
                    SessionVerificationPrompt(
                        phase = SessionVerificationPhase.Failed,
                        message = "Verification failed. You can retry from Settings.",
                    )
            }

            override fun didCancel() {
                Log.w(VERIFY_LOG, "didCancel phase=${_sessionVerificationPrompt.value?.phase}")
                if (System.currentTimeMillis() < suppressCancelUntilMs.get()) {
                    Log.i(VERIFY_LOG, "didCancel ignored while replacing a stuck flow")
                    return
                }
                // A finish and a cancel can cross. Keep the success if the MAC already landed.
                if (_sessionVerificationPrompt.value?.phase == SessionVerificationPhase.Finished) return
                sasStarted.set(false)
                requestAccepted.set(false)
                userConfirmedMatch.set(false)
                pendingIncomingRequest = null
                _sessionVerificationPrompt.value =
                    SessionVerificationPrompt(
                        phase = SessionVerificationPhase.Failed,
                        message = "Verification was cancelled or timed out. Start it again from Settings and confirm the emoji on both devices.",
                    )
            }

            override fun didFinish() {
                Log.i(VERIFY_LOG, "didFinish")
                sasStarted.set(false)
                requestAccepted.set(false)
                userConfirmedMatch.set(false)
                pendingIncomingRequest = null
                _sessionVerificationPrompt.value =
                    SessionVerificationPrompt(
                        phase = SessionVerificationPhase.Finished,
                        message = "Session verified. Messages should show as verified on other clients.",
                    )
            }
        }

    override suspend fun setHomeserver(serverOrName: String): Result<HomeserverLoginDetails> =
        withContext(dispatchers.io) {
            runCatching {
                mutex.withLock {
                    closeClientLocked()
                    // A fresh sign-in always starts from an empty store. Reusing a crypto store
                    // that belongs to an earlier session gives this device keys that disagree
                    // with the server, and other clients then reject our verification MAC.
                    // The saved session belonged to that store, so it is dropped together with it.
                    runCatching { File(appContext.filesDir, "matrix/active").deleteRecursively() }
                    sessionStore.markInvalidAndClearActive(false)
                    val paths = newSessionPaths()
                    sessionPaths = paths
                    val builder = newBuilderForDiscovery(paths).serverNameOrHomeserverUrl(serverOrName)
                    val c = try {
                        builder.build()
                    } finally {
                        builder.close()
                    }
                    client = c
                    c.homeserverLoginDetails().use { details ->
                        HomeserverLoginDetails(
                            supportsPasswordLogin = details.supportsPasswordLogin(),
                            supportsRegistration = false,
                            supportsOidc = details.supportsOauthLogin(),
                            supportsQrLogin = true,
                        )
                    }
                }
            }
        }

    override suspend fun login(username: String, password: String): Result<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                val c = mutex.withLock {
                    val cc = client ?: error("Call setHomeserver first")
                    try {
                        cc.login(
                            username = username,
                            password = password,
                            initialDeviceName = "Teletrix",
                            deviceId = null,
                        )
                    } catch (t: Throwable) {
                        if (!t.isAlreadyAuthenticated()) throw t
                    }
                    sessionStore.updateFromSdkSession(cc.session())
                    cc
                }
                completeLoginSetup(c)
            }
        }

    override suspend fun restoreSession(): Result<Unit> =
        withContext(dispatchers.io) {
            runCatching {
                val session = sessionStore.readPersistedSession() ?: error("No session")
                val c = mutex.withLock {
                    closeClientLocked()
                    val paths = newSessionPaths()
                    sessionPaths = paths
                    val builder = newBuilderForRestore(paths, session)
                        .homeserverUrl(session.homeserverUrl)
                        .username(session.userId)
                    val cc = try {
                        builder.build()
                    } finally {
                        builder.close()
                    }
                    try {
                        cc.restoreSession(session)
                    } catch (t: Throwable) {
                        if (!t.isAlreadyAuthenticated()) throw t
                    }
                    sessionStore.updateFromSdkSession(cc.session())
                    cc
                }
                completeLoginSetup(c)
            }
        }

    override fun hasPersistedSession(): Boolean = sessionStore.hasPersistedSession()

    override suspend fun logout() =
        withContext(dispatchers.io) {
            runCatching { unregisterHttpPusher() }
            val paths = mutex.withLock {
                runCatching { client?.logout() }
                val paths = sessionPaths
                closeClientLocked()
                sessionStore.markInvalidAndClearActive(false)
                _isLoggedIn.value = false
                _account.value = null
                _rooms.value = emptyList()
                _timeline.value = emptyMap()
                _roomMembers.value = emptyMap()
                unreadHolds.clear()
                unreadHoldsLoaded = false
                activeConversationRoomId = null
                registeredPushkey = null
                paths
            }
            runCatching { paths?.root?.deleteRecursively() }
            Unit
        }

    override suspend fun observeTimeline(roomId: MatrixRoomId) =
        withContext(dispatchers.io) {
            mutex.withLock {
                stopTimelineLocked()
                timelineItems.clear()
            }
            val snapshot = mutex.withLock {
                val cc = client
                val ss = syncService
                if (cc == null || ss == null) null else cc to ss
            } ?: return@withContext
            val (c, sync) = snapshot
            val room = openJoinedRoom(c, sync, roomId.value)
            if (room == null) {
                Log.w("NgSpaces", "joined room ${roomId.value} is not ready to open yet")
                return@withContext
            }
            val timeline = liveTimeline(room)
            mutex.withLock {
                activeTimeline = timeline
                activeTimelineRoomId = roomId.value
            }
            val own = c.userId()
            timelineHandle = timeline.addListener(
                object : TimelineListener {
                    override fun onUpdate(diffs: List<TimelineDiff>) {
                        diffs.forEach { TimelineDiffApplier.apply(timelineItems, it) }
                        val mapped = TimelineMessageMapper.mapItems(roomId, own, timelineItems)
                        _timeline.update { it + (roomId.value to mapped) }
                    }
                },
            )
            scope.launch(dispatchers.io) {
                runCatching {
                    val members = loadRoomMembersFor(room)
                    _roomMembers.update { it + (roomId.value to members) }
                }
            }
        }

    /** Live timeline with read receipts attached to the message each person has read up to. */
    private suspend fun liveTimeline(room: Room): Timeline {
        val config = TimelineConfiguration(
            focus = TimelineFocus.Live(hideThreadedEvents = false),
            filter = TimelineFilter.All,
            internalIdPrefix = null,
            dateDividerMode = DateDividerMode.DAILY,
            trackReadReceipts = TimelineReadReceiptTracking.MESSAGE_LIKE_EVENTS,
            reportUtds = false,
        )
        return try {
            room.timelineWithConfiguration(config)
        } finally {
            runCatching { config.destroy() }
        }
    }

    override suspend fun sendText(roomId: MatrixRoomId, body: String, mentions: List<MentionTag>) {
        withContext(dispatchers.io) {
            if (activeTimelineRoomId != roomId.value) return@withContext
            val timeline = activeTimeline ?: return@withContext
            val linked = linkMentions(body, mentions)
            val mentioned = (mentions.map { it.userId } + parseMentionUserIds(linked)).distinct()
            val roomMention = bodyMentionsRoom(body)
            val msg = messageEventContentFromMarkdown(linked)
                .withMentions(Mentions(userIds = mentioned, room = roomMention))
            try {
                timeline.send(msg)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Log.w("NgSend", "sendText failed", t)
            } finally {
                runCatching { msg.close() }
            }
        }
    }

    override suspend fun sendReply(
        roomId: MatrixRoomId,
        replyToEventId: String,
        body: String,
        mentions: List<MentionTag>,
    ) {
        withContext(dispatchers.io) {
            if (activeTimelineRoomId != roomId.value) return@withContext
            val timeline = activeTimeline ?: return@withContext
            // Matrix event ids keep their leading "$"; the SDK rejects the id without it.
            val eventId = replyToEventId.trim()
            if (eventId.isEmpty() || eventId.startsWith("txn:")) return@withContext
            val linked = linkMentions(body, mentions)
            val mentioned = (mentions.map { it.userId } + parseMentionUserIds(linked)).distinct()
            val roomMention = bodyMentionsRoom(body)
            val msg = messageEventContentFromMarkdown(linked)
                .withMentions(Mentions(userIds = mentioned, room = roomMention))
            try {
                timeline.sendReply(msg, eventId)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Log.w("NgSend", "sendReply failed", t)
            } finally {
                runCatching { msg.close() }
            }
        }
    }

    override suspend fun sendAttachment(
        roomId: MatrixRoomId,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ) {
        withContext(dispatchers.io) {
            if (activeTimelineRoomId != roomId.value) return@withContext
            val timeline = activeTimeline ?: return@withContext
            val mime = mimeType.ifBlank { "application/octet-stream" }
            runCatching {
                if (mime.startsWith("image/", ignoreCase = true)) {
                    sendImageAttachment(timeline, bytes, fileName, mime)
                } else {
                    sendFileAttachment(timeline, bytes, fileName, mime)
                }
            }
        }
    }

    override suspend fun sendSticker(
        roomId: MatrixRoomId,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ) {
        withContext(dispatchers.io) {
            if (activeTimelineRoomId != roomId.value) return@withContext
            val (c, sync) = mutex.withLock {
                val cc = client
                val ss = syncService
                if (cc == null || ss == null) null else cc to ss
            } ?: return@withContext
            val mime = mimeType.ifBlank { "application/octet-stream" }
            val mxc = c.uploadMedia(
                mime,
                bytes,
                object : ProgressWatcher {
                    override fun transmissionProgress(progress: org.matrix.rustcomponents.sdk.TransmissionProgress) = Unit
                },
            )
            val body = fileName.substringBeforeLast('.').ifBlank { "Sticker" }
            val content = org.json.JSONObject()
                .put("body", body)
                .put("url", mxc)
                .put(
                    "info",
                    org.json.JSONObject()
                        .put("mimetype", mime)
                        .put("w", 512)
                        .put("h", 512)
                        .put("size", bytes.size),
                )
                .toString()
            val room = sync.roomListService().room(roomId.value)
            room.sendRaw("m.sticker", content)
        }
    }

    override suspend fun paginateBack(roomId: MatrixRoomId): Boolean =
        withContext(dispatchers.io) {
            if (activeTimelineRoomId != roomId.value) return@withContext false
            runCatching { activeTimeline?.paginateBackwards(50u) }.getOrNull() == true
        }

    override suspend fun ensureEventInTimeline(roomId: MatrixRoomId, eventId: String): Boolean =
        withContext(dispatchers.io) {
            val target = eventId.trim()
            if (target.isEmpty() || target.startsWith("txn:")) return@withContext false
            if (timelineContains(roomId.value, target)) return@withContext true
            repeat(20) {
                val more = paginateBack(roomId)
                if (timelineContains(roomId.value, target)) return@withContext true
                if (!more) return@withContext false
            }
            timelineContains(roomId.value, target)
        }

    private fun timelineContains(roomId: String, eventId: String): Boolean =
        _timeline.value[roomId].orEmpty().any { it.eventId.value == eventId }

    override fun setActiveConversation(roomId: String?) {
        activeConversationRoomId = roomId
    }

    override suspend fun registerHttpPusher(pushkey: String) =
        withContext(dispatchers.io) {
            val key = pushkey.trim()
            if (key.isEmpty()) return@withContext
            val c = mutex.withLock { client } ?: return@withContext
            val ids = PusherIdentifiers(key, pusherAppId)
            val data = HttpPusherData(
                "https://matrix.gateway.unifiedpush.org/_matrix/push/v1/notify",
                PushFormat.EVENT_ID_ONLY,
                "{}",
            )
            val kind = PusherKind.Http(data)
            c.setPusher(
                identifiers = ids,
                kind = kind,
                appDisplayName = "Teletrix",
                deviceDisplayName = "Teletrix",
                profileTag = null,
                lang = "en",
            )
            registeredPushkey = key
        }

    override suspend fun unregisterHttpPusher() =
        withContext(dispatchers.io) {
            val key = registeredPushkey ?: return@withContext
            val c = mutex.withLock { client } ?: return@withContext
            val ids = PusherIdentifiers(key, pusherAppId)
            runCatching { c.deletePusher(ids) }
            registeredPushkey = null
        }

    override suspend fun notificationForEvent(roomId: String, eventId: String): IncomingNotification? =
        withContext(dispatchers.io) {
            if (!ensureClientReady()) return@withContext null
            val (c, sync) = mutex.withLock {
                val cc = client
                val ss = syncService
                if (cc == null || ss == null) null else cc to ss
            } ?: return@withContext null
            val setup = NotificationProcessSetup.SingleProcess(sync)
            val nc = try {
                c.notificationClient(setup)
            } finally {
                runCatching { setup.destroy() }
            }
            try {
                when (val status = nc.getNotification(roomId, eventId)) {
                    is NotificationStatus.Event -> {
                        val item = status.item
                        try {
                            mapNotificationItem(item, roomId, c.userId())
                        } finally {
                            runCatching { item.destroy() }
                        }
                    }
                    is NotificationStatus.EventFilteredOut ->
                        loadReactionNotification(roomId, eventId, c.userId())
                    else ->
                        loadReactionNotification(roomId, eventId, c.userId())
                }
            } finally {
                runCatching { nc.destroy() }
            }
        }

    private suspend fun ensureClientReady(): Boolean {
        if (mutex.withLock { client } != null && _isLoggedIn.value) return true
        return restoreSession().isSuccess && _isLoggedIn.value
    }

    private suspend fun mapNotificationItem(
        item: NotificationItem,
        roomId: String,
        ownUserId: String,
    ): IncomingNotification? {
        if (roomId == activeConversationRoomId) return null
        val senderId = when (val ev = item.event) {
            is NotificationEvent.Timeline -> ev.event.senderId()
            is NotificationEvent.Invite -> ev.sender
            else -> return null
        }
        if (senderId == ownUserId) return null
        val isInvite = item.event is NotificationEvent.Invite
        val reaction = parseReaction(item.rawEvent)
        val sender = item.senderInfo.displayName?.takeIf { it.isNotBlank() }
            ?: senderId.substringAfter("@").substringBefore(":").ifBlank { senderId }
        val roomTitle = item.roomInfo.displayName?.takeIf { it.isNotBlank() } ?: roomId
        val eventId = when (val ev = item.event) {
            is NotificationEvent.Timeline -> ev.event.eventId()
            else -> ""
        }
        val body = when {
            isInvite -> "Invited you to a room"
            reaction != null -> "$sender reacted ${reaction.key}"
            else -> bodyFromRawEvent(item.rawEvent)
        }
        val mapped = IncomingNotification(
            roomId = roomId,
            eventId = eventId,
            roomTitle = roomTitle,
            senderName = sender,
            body = body,
            isDirect = item.roomInfo.isDirect || item.roomInfo.isDm,
            isInvite = isInvite,
            noisy = item.isNoisy != false,
            hasMention = item.hasMention == true || mentionsOwnUser(item.rawEvent, ownUserId),
            isReaction = reaction != null,
            jumpEventId = reaction?.eventId,
            reactionKey = reaction?.key,
        )
        return if (shouldNotify(mapped, ownUserId, reaction?.eventId, roomId)) mapped else null
    }

    private fun bodyFromRawEvent(raw: String?): String {
        if (raw.isNullOrBlank()) return "New message"
        return runCatching {
            val content = JSONObject(raw).optJSONObject("content") ?: return@runCatching "New message"
            val type = content.optString("msgtype")
            val body = content.optString("body").trim()
            when {
                type == "m.image" -> "Photo"
                type == "m.video" -> "Video"
                type == "m.audio" -> if (content.optJSONObject("info")?.optString("org.matrix.msc3245.voice") != null ||
                    content.has("org.matrix.msc3245.voice")
                ) {
                    "Voice message"
                } else {
                    "Audio"
                }
                type == "m.sticker" || raw.contains("\"m.sticker\"") -> "Sticker"
                type == "m.file" -> "File"
                body.isNotBlank() -> body.lines().first().take(180)
                else -> "New message"
            }
        }.getOrDefault("New message")
    }

    private fun emitIncoming(item: IncomingNotification) {
        if (item.roomId == activeConversationRoomId) return
        if (item.jumpEventId == null && item.isReaction) return
        _incomingNotifications.tryEmit(item)
    }

    override fun setNotificationPrefs(prefs: NotificationPrefs) {
        notificationPrefs = prefs
    }

    override suspend fun applyNotificationPrefs() =
        withContext(dispatchers.io) {
            val c = mutex.withLock { client } ?: return@withContext
            val prefs = notificationPrefs
            val settings = try {
                c.getNotificationSettings()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                return@withContext
            }
            try {
                val dmMode = if (!prefs.enabled) ChatNotifyMode.Off else prefs.privateChats
                val groupMode = if (!prefs.enabled) ChatNotifyMode.Off else prefs.groups
                listOf(false, true).forEach { encrypted ->
                    try {
                        settings.setDefaultRoomNotificationMode(encrypted, true, toSdkMode(dmMode))
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                    }
                    try {
                        settings.setDefaultRoomNotificationMode(encrypted, false, toSdkMode(groupMode))
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                    }
                }
                try {
                    settings.setUserMentionEnabled(prefs.enabled)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                }
                try {
                    settings.setRoomMentionEnabled(prefs.enabled)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                }
                if (prefs.enabled && prefs.reactions) {
                    try {
                        settings.setCustomPushRule(
                            REACTION_RULE_ID,
                            RuleKind.Override,
                            listOf(Action.Notify),
                            listOf(PushCondition.EventMatch("type", "m.reaction")),
                        )
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                    }
                }
            } finally {
                runCatching { settings.destroy() }
            }
        }

    private fun toSdkMode(mode: ChatNotifyMode): RoomNotificationMode = when (mode) {
        ChatNotifyMode.All -> RoomNotificationMode.ALL_MESSAGES
        ChatNotifyMode.Mentions -> RoomNotificationMode.MENTIONS_AND_KEYWORDS_ONLY
        ChatNotifyMode.Off -> RoomNotificationMode.MUTE
    }

    private suspend fun shouldNotify(
        item: IncomingNotification,
        ownUserId: String,
        reactionTargetId: String?,
        roomId: String,
    ): Boolean {
        val prefs = notificationPrefs
        if (!prefs.enabled) return false
        if (item.roomId == activeConversationRoomId) return false
        if (item.isInvite) return true
        if (item.isReaction) {
            if (!prefs.reactions || reactionTargetId.isNullOrBlank()) return false
            return reactionTargetsOwnMessage(roomId, reactionTargetId, ownUserId)
        }
        val mode = if (item.isDirect) prefs.privateChats else prefs.groups
        return when (mode) {
            ChatNotifyMode.All -> true
            ChatNotifyMode.Mentions -> item.hasMention
            ChatNotifyMode.Off -> false
        }
    }

    private suspend fun reactionTargetsOwnMessage(roomId: String, eventId: String, ownUserId: String): Boolean {
        val room = mutex.withLock { client?.getRoom(roomId) } ?: return false
        return runCatching {
            val ev = room.loadOrFetchEvent(eventId)
            try {
                ev.senderId() == ownUserId
            } finally {
                runCatching { ev.destroy() }
            }
        }.getOrDefault(false)
    }

    private suspend fun loadReactionNotification(
        roomId: String,
        eventId: String,
        ownUserId: String,
    ): IncomingNotification? {
        val room = mutex.withLock { client?.getRoom(roomId) } ?: return null
        val ev = runCatching { room.loadOrFetchEvent(eventId) }.getOrNull() ?: return null
        return try {
            val raw = runCatching { ev.content() }.getOrNull()
            val reaction = parseReactionFromRelated(ev, raw) ?: return null
            if (ev.senderId() == ownUserId) return null
            if (!notificationPrefs.enabled || !notificationPrefs.reactions) return null
            if (!reactionTargetsOwnMessage(roomId, reaction.eventId, ownUserId)) return null
            val sender = ev.senderId().substringAfter("@").substringBefore(":")
            val summary = _rooms.value.firstOrNull { it.roomId.value == roomId }
            IncomingNotification(
                roomId = roomId,
                eventId = ev.eventId(),
                roomTitle = summary?.title ?: roomId,
                senderName = sender,
                body = "$sender reacted ${reaction.key}",
                isDirect = summary?.presentationKind == RoomPresentationKind.DirectMessage,
                isReaction = true,
                jumpEventId = reaction.eventId,
                reactionKey = reaction.key,
            )
        } finally {
            runCatching { ev.destroy() }
        }
    }

    private data class ReactionRef(val eventId: String, val key: String)

    private fun parseReaction(raw: String?): ReactionRef? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(raw)
            val type = obj.optString("type")
            val content = obj.optJSONObject("content") ?: obj
            val relates = content.optJSONObject("m.relates_to") ?: return@runCatching null
            if (type.isNotBlank() && type != "m.reaction" &&
                relates.optString("rel_type") != "m.annotation"
            ) {
                return@runCatching null
            }
            val target = relates.optString("event_id")
            val key = relates.optString("key").ifBlank { "❤" }
            if (target.isBlank()) null else ReactionRef(target, key)
        }.getOrNull()
    }

    private fun parseReactionFromRelated(
        ev: org.matrix.rustcomponents.sdk.TimelineEvent,
        content: org.matrix.rustcomponents.sdk.TimelineEventContent?,
    ): ReactionRef? {
        try {
            val related = (content as? org.matrix.rustcomponents.sdk.TimelineEventContent.MessageLike)
                ?.content as? org.matrix.rustcomponents.sdk.MessageLikeEventContent.ReactionContent
            if (related != null) {
                return ReactionRef(related.relatedEventId, "❤")
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { content?.destroy() }
        }
        return null
    }

    override suspend fun loadMatrixMediaThumbnail(
        sourceJson: String,
        maxWidth: Long,
        maxHeight: Long,
    ): ByteArray? =
        withContext(dispatchers.io) {
            val c = mutex.withLock { client } ?: return@withContext null
            runCatching {
                MediaSource.fromJson(sourceJson).use { src ->
                    c.getMediaThumbnail(src, maxWidth.toULong(), maxHeight.toULong())
                }
            }.getOrNull()
        }

    override suspend fun loadMatrixMediaContent(sourceJson: String): ByteArray? =
        withContext(dispatchers.io) {
            val c = mutex.withLock { client } ?: return@withContext null
            runCatching {
                MediaSource.fromJson(sourceJson).use { src ->
                    c.getMediaContent(src)
                }
            }.getOrNull()
        }

    override suspend fun loadMatrixAvatarThumbnail(
        mxcUri: String?,
        maxWidth: Long,
        maxHeight: Long,
    ): ByteArray? {
        val uri = mxcUri?.trim().orEmpty()
        if (uri.isEmpty()) return null
        val cacheKey = "$uri@${maxWidth}x$maxHeight"
        avatarCache.get(cacheKey)?.let { return it }
        return withContext(dispatchers.io) {
            val c = mutex.withLock { client } ?: return@withContext null
            runCatching {
                MediaSource.fromUrl(uri).use { src ->
                    c.getMediaThumbnail(src, maxWidth.toULong(), maxHeight.toULong())
                }
            }.getOrNull()?.also { avatarCache.put(cacheKey, it) }
        }
    }

    override suspend fun refreshAccount() {
        val c = mutex.withLock { client } ?: return
        val session = runCatching { c.session() }.getOrNull() ?: return
        val name = runCatching { c.displayName() }.getOrNull()?.takeIf { it.isNotBlank() }
        val avatar = runCatching { c.cachedAvatarUrl() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: runCatching { c.avatarUrl() }.getOrNull()?.takeIf { it.isNotBlank() }
        _account.value = AccountSummary(
            userId = session.userId,
            displayName = name,
            avatarMxc = avatar,
            homeserverUrl = session.homeserverUrl,
            deviceId = session.deviceId,
        )
    }

    override suspend fun topLevelSpaces(): List<SpaceEntry> = withContext(dispatchers.io) {
        val c = mutex.withLock { client } ?: return@withContext emptyList()
        runCatching {
            val service = c.spaceService()
            try {
                service.topLevelJoinedSpaces()
                    .map { it.toEntry() }
                    .sortedBy { it.title.lowercase() }
            } finally {
                service.close()
            }
        }.getOrElse {
            Log.w("NgSpaces", "could not load spaces", it)
            emptyList()
        }
    }

    override suspend fun spaceContents(spaceId: String): SpaceContents = withContext(dispatchers.io) {
        val c = mutex.withLock { client } ?: return@withContext SpaceContents(null, emptyList())
        runCatching {
            val service = c.spaceService()
            try {
                val list = service.spaceRoomList(spaceId)
                try {
                    var pages = 0
                    while (pages < 8) {
                        val state = list.paginationState()
                        if (state is SpaceRoomListPaginationState.Idle && state.endReached) break
                        list.paginate()
                        pages++
                    }
                    val space = list.space()?.toEntry()
                    val listed = list.rooms()
                        .map { it.toEntry() }
                        .filter { it.roomId != spaceId }
                        .associateBy { it.roomId }
                        .toMutableMap()
                    val declared = declaredSpaceChildren(c, spaceId)
                    val named = hierarchyChildren(c, spaceId)
                    val known = _rooms.value.associateBy { it.roomId.value }
                    Log.i(
                        "NgSpaces",
                        "space $spaceId hierarchy=${listed.size} declared=${declared.size} named=${named.size}",
                    )
                    for (child in declared) {
                        if (child.roomId in listed || child.roomId == spaceId) continue
                        if (listed.size >= 80) break
                        val via = child.via.ifEmpty { listOfNotNull(serverOf(child.roomId)) }
                        val base = named[child.roomId]?.copy(via = via) ?: SpaceEntry(
                            roomId = child.roomId,
                            title = "Room",
                            topic = null,
                            avatarMxc = null,
                            memberCount = 0,
                            childCount = 0,
                            isSpace = false,
                            joined = false,
                            via = via,
                            joinNote = "Name hidden until you join",
                        )
                        val summary = known[child.roomId]
                        listed[child.roomId] = if (summary == null) {
                            base
                        } else {
                            base.copy(
                                title = summary.title.ifBlank { base.title },
                                avatarMxc = summary.avatarMxc ?: base.avatarMxc,
                                isSpace = summary.isSpace || base.isSpace,
                                joined = true,
                            )
                        }
                    }
                    val children = listed.values.sortedWith(
                        compareBy<SpaceEntry> { !it.joined }.thenBy { it.title.lowercase() },
                    )
                    SpaceContents(space, children)
                } finally {
                    list.close()
                }
            } finally {
                service.close()
            }
        }.getOrElse {
            Log.w("NgSpaces", "could not load space $spaceId", it)
            SpaceContents(null, emptyList())
        }
    }

    private fun SpaceRoom.toEntry(): SpaceEntry {
        val members = numJoinedMembers.toLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val children = childrenCount.toLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        return SpaceEntry(
            roomId = roomId,
            title = displayName?.takeIf { it.isNotBlank() } ?: roomId,
            topic = topic?.takeIf { it.isNotBlank() },
            avatarMxc = avatarUrl?.takeIf { it.isNotBlank() },
            memberCount = members,
            childCount = children,
            isSpace = roomType is RoomType.Space,
            joined = state == Membership.JOINED,
            via = via.orEmpty(),
            joinNote = joinRuleNote(joinRule),
        )
    }

    private data class DeclaredChild(val roomId: String, val via: List<String>)

    /**
     * `m.space.child` state on a space this account has joined. The hierarchy summary leaves out
     * rooms the account has not joined; the state events still name them.
     */
    private fun declaredSpaceChildren(client: Client, spaceId: String): List<DeclaredChild> {
        val session = runCatching { client.session() }.getOrNull() ?: return emptyList()
        val token = session.accessToken?.takeIf { it.isNotBlank() } ?: return emptyList()
        val encoded = URLEncoder.encode(spaceId, Charsets.UTF_8.name())
        val url = URL("${session.homeserverUrl.trimEnd('/')}/_matrix/client/v3/rooms/$encoded/state")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        val body = try {
            if (conn.responseCode !in 200..299) {
                Log.w("NgSpaces", "space state ${conn.responseCode} for $spaceId")
                return emptyList()
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            Log.w("NgSpaces", "space state failed for $spaceId", t)
            return emptyList()
        } finally {
            conn.disconnect()
        }
        val arr = runCatching { JSONArray(body) }.getOrElse { return emptyList() }
        val out = ArrayList<DeclaredChild>()
        for (i in 0 until arr.length()) {
            val event = arr.optJSONObject(i) ?: continue
            if (event.optString("type") != "m.space.child") continue
            val id = event.optString("state_key")
            if (!id.startsWith("!")) continue
            val content = event.optJSONObject("content") ?: JSONObject()
            if (content.length() == 0) continue
            val viaJson = content.optJSONArray("via")
            val via = buildList {
                if (viaJson != null) {
                    for (v in 0 until viaJson.length()) {
                        viaJson.optString(v).takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }
            out += DeclaredChild(id, via)
        }
        return out
    }

    /**
     * Names and topics from the space hierarchy. This server hides previews of rooms the account
     * has not joined, so a child can be missing here even though `m.space.child` lists it.
     */
    private fun hierarchyChildren(client: Client, spaceId: String): Map<String, SpaceEntry> {
        val encoded = URLEncoder.encode(spaceId, Charsets.UTF_8.name())
        val raw = authedGet(
            client,
            "/_matrix/client/v1/rooms/$encoded/hierarchy?suggested_only=false&max_depth=1&limit=50",
        ) ?: return emptyMap()
        val rooms = runCatching { JSONObject(raw).optJSONArray("rooms") }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, SpaceEntry>()
        for (i in 0 until rooms.length()) {
            val room = rooms.optJSONObject(i) ?: continue
            val id = room.optString("room_id")
            if (!id.startsWith("!") || id == spaceId) continue
            val children = room.optJSONArray("children_state")?.length() ?: 0
            val members = room.optInt("num_joined_members")
            out[id] = SpaceEntry(
                roomId = id,
                title = room.optString("name").takeIf { it.isNotBlank() }
                    ?: room.optString("canonical_alias").takeIf { it.isNotBlank() }
                    ?: "Room",
                topic = room.optString("topic").takeIf { it.isNotBlank() },
                avatarMxc = room.optString("avatar_url").takeIf { it.isNotBlank() },
                memberCount = members,
                childCount = children,
                isSpace = room.optString("room_type") == "m.space",
                joined = false,
                via = listOfNotNull(serverOf(id)),
                joinNote = joinRuleStringNote(room.optString("join_rule")),
            )
        }
        return out
    }

    private fun serverOf(roomId: String): String? =
        roomId.substringAfter(':', "").takeIf { it.isNotBlank() }

    private fun joinRuleNote(rule: JoinRule?): String? = when (rule) {
        is JoinRule.Invite, is JoinRule.Private -> "Invite only"
        is JoinRule.Knock, is JoinRule.KnockRestricted -> "Knock to join"
        is JoinRule.Restricted -> "Restricted"
        else -> null
    }

    override suspend fun joinListedRoom(roomId: String, via: List<String>): Result<Boolean> =
        withContext(dispatchers.io) {
            runCatching {
                val c = mutex.withLock { client } ?: error("Not signed in")
                val room = c.joinRoomByIdOrAlias(roomId, via)
                try {
                    room.isSpace()
                } finally {
                    runCatching { room.close() }
                }
            }
        }

    override suspend fun searchPublicRooms(query: String): DirectoryPage = withContext(dispatchers.io) {
        val c = mutex.withLock { client } ?: return@withContext DirectoryPage(emptyList(), false)
        directoryMutex.withLock {
            directoryQuery = query.trim().takeIf { it.isNotEmpty() }
            directoryRooms.clear()
            val page = fetchPublicRooms(c, directoryQuery, null)
            directoryRooms.addAll(page.entries)
            directoryNextBatch = page.next
            Log.i("NgSpaces", "directory '${directoryQuery.orEmpty()}' ${directoryRooms.size} more=${page.next != null}")
            DirectoryPage(directoryRooms.toList(), page.next != null)
        }
    }

    override suspend fun morePublicRooms(): DirectoryPage = withContext(dispatchers.io) {
        val c = mutex.withLock { client } ?: return@withContext DirectoryPage(emptyList(), false)
        directoryMutex.withLock {
            val next = directoryNextBatch
            if (next.isNullOrBlank()) return@withLock DirectoryPage(directoryRooms.toList(), false)
            val page = fetchPublicRooms(c, directoryQuery, next)
            val seen = directoryRooms.mapTo(HashSet()) { it.roomId }
            directoryRooms.addAll(page.entries.filter { it.roomId !in seen })
            directoryNextBatch = page.next
            DirectoryPage(directoryRooms.toList(), page.next != null)
        }
    }

    private data class PublicPage(val entries: List<DirectoryEntry>, val next: String?)

    private fun fetchPublicRooms(client: Client, query: String?, since: String?): PublicPage {
        val body = JSONObject().put("limit", 30)
        if (!query.isNullOrBlank()) {
            body.put("filter", JSONObject().put("generic_search_term", query))
        }
        if (!since.isNullOrBlank()) body.put("since", since)
        val raw = authedPost(client, "/_matrix/client/v3/publicRooms", body.toString())
            ?: return PublicPage(emptyList(), null)
        val json = runCatching { JSONObject(raw) }.getOrElse { return PublicPage(emptyList(), null) }
        val chunk = json.optJSONArray("chunk") ?: JSONArray()
        val known = _rooms.value.associateBy { it.roomId.value }
        val entries = buildList {
            for (i in 0 until chunk.length()) {
                val room = chunk.optJSONObject(i) ?: continue
                val id = room.optString("room_id")
                if (!id.startsWith("!")) continue
                val existing = known[id]
                add(
                    DirectoryEntry(
                        roomId = id,
                        title = room.optString("name").takeIf { it.isNotBlank() }
                            ?: room.optString("canonical_alias").takeIf { it.isNotBlank() }
                            ?: "Room",
                        topic = room.optString("topic").takeIf { it.isNotBlank() },
                        alias = room.optString("canonical_alias").takeIf { it.isNotBlank() },
                        avatarMxc = room.optString("avatar_url").takeIf { it.isNotBlank() },
                        memberCount = room.optInt("num_joined_members"),
                        joined = existing != null,
                        isSpace = room.optString("room_type") == "m.space" || existing?.isSpace == true,
                        joinNote = joinRuleStringNote(room.optString("join_rule")),
                        via = listOfNotNull(serverOf(id)),
                    ),
                )
            }
        }
        return PublicPage(entries, json.optString("next_batch").takeIf { it.isNotBlank() })
    }

    private fun authedGet(client: Client, pathAndQuery: String): String? =
        authed(client, pathAndQuery, "GET", null)

    private fun authedPost(client: Client, path: String, body: String): String? =
        authed(client, path, "POST", body)

    private fun authed(client: Client, pathAndQuery: String, method: String, body: String?): String? {
        val session = runCatching { client.session() }.getOrNull() ?: return null
        val token = session.accessToken.takeIf { it.isNotBlank() } ?: return null
        val url = URL("${session.homeserverUrl.trimEnd('/')}$pathAndQuery")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                Log.w("NgSpaces", "$method $pathAndQuery -> $code ${text.take(180)}")
                null
            } else {
                text
            }
        } catch (t: Throwable) {
            Log.w("NgSpaces", "$method $pathAndQuery failed", t)
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Sliding sync can lag a fresh join, so try the room list and then the client room store. */
    private suspend fun openJoinedRoom(client: Client, sync: ClientSyncService, roomId: String): Room? {
        repeat(6) { attempt ->
            runCatching { sync.roomListService().room(roomId) }.getOrNull()?.let { return it }
            runCatching { client.getRoom(roomId) }.getOrNull()?.let { return it }
            if (attempt < 5) delay(300)
        }
        return null
    }

    private fun joinRuleStringNote(rule: String): String? = when (rule) {
        "knock", "knock_restricted" -> "Knock to join"
        "invite", "private" -> "Invite only"
        "restricted" -> "Restricted"
        else -> null
    }

    override suspend fun approveSessionVerification() {
        val showingNow = _sessionVerificationPrompt.value
        Log.i(VERIFY_LOG, "match tapped phase=${showingNow?.phase}")
        if (!approveInFlight.compareAndSet(false, true)) {
            Log.i(VERIFY_LOG, "match already sending")
            if (showingNow?.phase == SessionVerificationPhase.SasCompare) {
                _sessionVerificationPrompt.value =
                    showingNow.copy(message = "Confirming the match with your other device…")
            }
            return
        }
        val generation = approveGeneration.incrementAndGet()
        val showing = _sessionVerificationPrompt.value
        if (showing?.phase != SessionVerificationPhase.SasCompare) {
            approveInFlight.set(false)
            return
        }
        userConfirmedMatch.set(true)
        _sessionVerificationPrompt.value =
            showing.copy(message = "Confirming the match with your other device…")
        // IO + NonCancellable: the SDK can emit SAS state while confirm() is running,
        // and a main-thread collector must not cancel that upload.
        try {
            withContext(dispatchers.io + NonCancellable) {
                val ctrl = sessionVerificationController
                if (ctrl == null) {
                    if (approveGeneration.get() == generation) {
                        _sessionVerificationPrompt.value =
                            SessionVerificationPrompt(
                                phase = SessionVerificationPhase.Failed,
                                emojis = showing.emojis,
                                message = "Verification is no longer active. Start it again from Settings.",
                            )
                    }
                    return@withContext
                }
                Log.i(VERIFY_LOG, "approveVerification start gen=$generation")
                ctrl.approveVerification()
                Log.i(VERIFY_LOG, "approveVerification returned gen=$generation")
                if (approveGeneration.get() != generation) return@withContext
                _sessionVerificationPrompt.update { current ->
                    when (current?.phase) {
                        SessionVerificationPhase.Finished,
                        SessionVerificationPhase.Failed,
                        -> current
                        else -> showing.copy(
                            message = "Match sent. Tap Match on your other device if it still shows the same emoji.",
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(VERIFY_LOG, "approveVerification failed", t)
            if (approveGeneration.get() == generation) {
                _sessionVerificationPrompt.update { current ->
                    if (current?.phase == SessionVerificationPhase.Finished) {
                        current
                    } else {
                        SessionVerificationPrompt(
                            phase = SessionVerificationPhase.Failed,
                            emojis = showing.emojis,
                            message = t.message?.takeIf { it.isNotBlank() }
                                ?: "Could not confirm the emoji match.",
                        )
                    }
                }
            }
        } finally {
            if (approveGeneration.get() == generation) {
                approveInFlight.set(false)
            }
        }
        watchUntilVerified(generation)
    }

    override suspend fun declineSessionVerification() =
        withContext(dispatchers.io + NonCancellable) {
            if (_sessionVerificationPrompt.value?.phase == SessionVerificationPhase.Finished) {
                return@withContext
            }
            val ctrl = sessionVerificationController ?: return@withContext
            try {
                ctrl.declineVerification()
            } catch (_: Throwable) {
            }
            if (_sessionVerificationPrompt.value?.phase == SessionVerificationPhase.Finished) return@withContext
            sasStarted.set(false)
            requestAccepted.set(false)
            pendingIncomingRequest = null
            _sessionVerificationPrompt.value =
                SessionVerificationPrompt(
                    phase = SessionVerificationPhase.Failed,
                    message = "Emoji did not match. Verification was cancelled.",
                )
        }

    override suspend fun cancelSessionVerification() =
        withContext(dispatchers.io) {
            val ctrl = sessionVerificationController ?: return@withContext
            try {
                ctrl.cancelVerification()
            } catch (_: Throwable) {
            }
        }

    override suspend fun dismissSessionVerificationPrompt() =
        withContext(dispatchers.io) {
            // The pending dialog is removed as soon as the emoji screen replaces it.
            // That removal fires onDismissRequest. Clearing SasCompare here would wipe
            // the emoji challenge. Finished and Failed must still close: Done is this call.
            _sessionVerificationPrompt.update { current ->
                if (current?.phase == SessionVerificationPhase.SasCompare) current else null
            }
        }

    override suspend fun continueSessionVerificationFromPending() =
        withContext(dispatchers.io) {
            if (sessionVerificationController == null) {
                val c = mutex.withLock { client } ?: return@withContext
                ensureSessionVerificationController(c)
            }
            if (!outgoingVerification.get()) {
                if (_sessionVerificationPrompt.value?.phase != SessionVerificationPhase.SasCompare) {
                    _sessionVerificationPrompt.value =
                        SessionVerificationPrompt(
                            phase = SessionVerificationPhase.Pending,
                            message = "Starting the emoji check on both devices.",
                        )
                }
                acceptIncomingVerification()
                return@withContext
            }
            if (!requestAccepted.get()) {
                _sessionVerificationPrompt.value =
                    SessionVerificationPrompt(
                        phase = SessionVerificationPhase.Pending,
                        message = "Accept the request on your other device. The emoji check starts after that.",
                    )
                return@withContext
            }
            // Accepted already: the other device (or the grace timer in
            // didAcceptVerificationRequest) starts SAS. Starting here too would cross starts.
            if (_sessionVerificationPrompt.value?.phase != SessionVerificationPhase.SasCompare) {
                _sessionVerificationPrompt.value =
                    SessionVerificationPrompt(
                        phase = SessionVerificationPhase.Pending,
                        message = "Starting the emoji check…",
                    )
            }
        }

    override suspend fun requestSessionVerification() =
        withContext(dispatchers.io) {
            val c = mutex.withLock { client }
            if (c == null) {
                _sessionVerificationPrompt.value =
                    SessionVerificationPrompt(
                        phase = SessionVerificationPhase.Failed,
                        message = "Sign in before verifying this session.",
                    )
                return@withContext
            }
            val blockReason = runCatching {
                c.encryption().use { enc ->
                    runCatching { enc.waitForE2eeInitializationTasks() }
                    when (enc.verificationState()) {
                        VerificationState.VERIFIED -> "This session is already verified."
                        else ->
                            if (enc.hasDevicesToVerifyAgainst() == true) {
                                null
                            } else {
                                "Sign in on another Matrix client first, then try again."
                            }
                    }
                }
            }.getOrElse { t ->
                t.message?.takeIf { it.isNotBlank() } ?: "Could not check verification state."
            }
            if (blockReason != null) {
                _sessionVerificationPrompt.value =
                    SessionVerificationPrompt(
                        phase = SessionVerificationPhase.Failed,
                        message = blockReason,
                    )
                return@withContext
            }
            runCatching { startOutboundSessionVerification(c) }
                .onFailure { t ->
                    _sessionVerificationPrompt.value =
                        SessionVerificationPrompt(
                            phase = SessionVerificationPhase.Failed,
                            message = t.message?.takeIf { it.isNotBlank() }
                                ?: "Could not request verification.",
                        )
                }
        }

    override suspend fun updateRoomReadReceipts(roomId: MatrixRoomId, lastReadEventId: String) =
        withContext(dispatchers.io) {
            if (activeTimelineRoomId != roomId.value) return@withContext
            val tl = activeTimeline ?: return@withContext
            val eid = lastReadEventId.trim()
            if (eid.isEmpty() || eid.startsWith("txn:")) return@withContext
            runCatching { tl.sendReadReceipt(ReceiptType.READ, eid) }
            runCatching { tl.sendReadReceipt(ReceiptType.FULLY_READ, eid) }
            val latestId = _timeline.value[roomId.value]
                .orEmpty()
                .lastOrNull { !it.eventId.value.startsWith("txn:") }
                ?.eventId
                ?.value
            if (latestId == eid) {
                markRoomReadLocally(roomId.value)
                _rooms.update { list ->
                    list.map { room ->
                        if (room.roomId.value == roomId.value) {
                            room.copy(unreadCount = 0, mentionCount = 0)
                        } else {
                            room
                        }
                    }
                }
            }
        }

    /**
     * Must run **without** holding [mutex]: sync + room list can call back into code that
     * needs the lock (e.g. [refreshRoomsFromClientLocked]); [Mutex] is not re-entrant.
     */
    private suspend fun completeLoginSetup(c: Client) {
        ensureUnreadHoldsLoaded()
        // Walk each room's history back to the read receipt so the unread counter
        // is not stuck on the single event the room list keeps in view.
        c.enableAutomaticBackpagination()
        c.setDelegate(delegate)
        // The saved sync position only delivers new events. Ask for a full
        // timeline once so each room is counted from the last hundred messages.
        val refetchTimelines = !timelineLimitMarker().exists()
        val sync = c.syncService()
            .withSharePos(!refetchTimelines)
            .withOfflineMode()
            .withRoomListTimelineLimit(100u)
            .finish()
        mutex.withLock {
            syncService = sync
            client = c
        }
        sync.start()
        if (refetchTimelines) {
            runCatching {
                timelineLimitMarker().apply {
                    parentFile?.mkdirs()
                    writeText("100")
                }
            }
        }
        startRoomListFromSyncLocked(sync)
        runCatching { refreshRoomsFromClientLocked() }
        _isLoggedIn.value = true
        startRoomPollingLocked()
        runCatching { refreshAccount() }
        runCatching { ensureSessionVerificationController(c) }
        runCatching { attachSyncNotifications(c) }
        runCatching { applyNotificationPrefs() }
    }

    private suspend fun attachSyncNotifications(c: Client) {
        c.registerNotificationHandler(
            object : SyncNotificationListener {
                override fun onNotification(notification: NotificationItem, roomId: String) {
                    scope.launch {
                        try {
                            val own = runCatching { c.userId() }.getOrNull() ?: return@launch
                            mapNotificationItem(notification, roomId, own)?.let(::emitIncoming)
                        } finally {
                            runCatching { notification.destroy() }
                        }
                    }
                }
            },
        )
    }

    private fun startRoomPollingLocked() {
        roomPollJob?.cancel()
        roomPollJob = scope.launch {
            while (isActive && _isLoggedIn.value) {
                runCatching { refreshRoomsFromClientLocked() }
                delay(2500)
            }
        }
    }

    /**
     * Legacy client room enumeration; often empty under sliding sync — real list comes from
     * [startRoomListFromSyncLocked], but we keep this as a slow fallback.
     */
    private suspend fun refreshRoomsFromClientLocked() {
        val c = mutex.withLock { client } ?: return
        val joined = c.rooms().filter { it.membership() == Membership.JOINED }
        if (joined.isEmpty()) return
        val mapped = joined.mapNotNull { room ->
            runCatching { mapSdkRoomToSummary(room) }.getOrNull()
        }.sortedByDescending { it.title }
        if (mapped.isNotEmpty()) {
            _rooms.value = sortRoomSummaries(mapped)
        }
    }

    private suspend fun closeClientLocked() {
        roomPollJob?.cancel()
        roomPollJob = null
        stopRoomListLocked()
        stopTimelineLocked()
        disposeSessionVerificationController()
        _roomMembers.value = emptyMap()
        runCatching { syncService?.stop() }
        runCatching { syncService?.destroy() }
        syncService = null
        runCatching { client?.close() }
        client = null
        // Keep matrix/active on disk. Logout is what deletes it, so the next
        // launch can restore the same crypto store and stay signed in.
        sessionPaths = null
    }

    private fun stopTimelineLocked() {
        timelineHandle?.cancel()
        timelineHandle = null
        runCatching { activeTimeline?.close() }
        activeTimeline = null
        activeTimelineRoomId = null
        timelineItems.clear()
    }

    private fun stopRoomListLocked() {
        roomListStreamHandle?.cancel()
        roomListStreamHandle = null
        runCatching { roomListAdapterResult?.close() }
        roomListAdapterResult = null
        runCatching { roomList?.close() }
        roomList = null
    }

    private suspend fun startRoomListFromSyncLocked(sync: ClientSyncService) {
        stopRoomListLocked()
        val rl = sync.roomListService().allRooms()
        roomList = rl
        val adapter = rl.entriesWithDynamicAdapters(
            50u,
            object : RoomListEntriesListener {
                override fun onUpdate(updates: List<RoomListEntriesUpdate>) {
                    scope.launch(dispatchers.io) {
                        runCatching { processRoomListUpdates(updates) }
                    }
                }
            },
        )
        roomListAdapterResult = adapter
        adapter.controller().resetToOnePage()
        roomListStreamHandle = adapter.entriesStream()
    }

    private suspend fun processRoomListUpdates(updates: List<RoomListEntriesUpdate>) {
        for (u in updates) {
            when (u) {
                is RoomListEntriesUpdate.Reset -> {
                    val mapped = u.values
                        .filter { it.membership() == Membership.JOINED }
                        .mapNotNull { runCatching { mapSdkRoomToSummary(it) }.getOrNull() }
                    _rooms.value = sortRoomSummaries(mapped)
                }
                is RoomListEntriesUpdate.Append -> {
                    val mapped = u.values
                        .filter { it.membership() == Membership.JOINED }
                        .mapNotNull { runCatching { mapSdkRoomToSummary(it) }.getOrNull() }
                    if (mapped.isEmpty()) continue
                    _rooms.update { cur ->
                        sortRoomSummaries((cur + mapped).distinctBy { it.roomId.value })
                    }
                }
                is RoomListEntriesUpdate.PushBack -> {
                    val room = u.value
                    if (room.membership() != Membership.JOINED) continue
                    val summary = runCatching { mapSdkRoomToSummary(room) }.getOrNull() ?: continue
                    _rooms.update { cur ->
                        sortRoomSummaries((cur + summary).distinctBy { it.roomId.value })
                    }
                }
                is RoomListEntriesUpdate.Insert -> {
                    val room = u.value
                    if (room.membership() != Membership.JOINED) continue
                    val summary = runCatching { mapSdkRoomToSummary(room) }.getOrNull() ?: continue
                    _rooms.update { cur ->
                        sortRoomSummaries((cur + summary).distinctBy { it.roomId.value })
                    }
                }
                is RoomListEntriesUpdate.Set -> {
                    val room = u.value
                    if (room.membership() != Membership.JOINED) continue
                    val summary = runCatching { mapSdkRoomToSummary(room) }.getOrNull() ?: continue
                    _rooms.update { cur ->
                        sortRoomSummaries(
                            cur.map { if (it.roomId.value == summary.roomId.value) summary else it },
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    private suspend fun mapSdkRoomToSummary(room: Room): RoomSummary {
        val isDm = runCatching { room.isDirect() }.getOrDefault(false)
        val isEncrypted = runCatching {
            room.encryptionState() == EncryptionState.ENCRYPTED
        }.getOrDefault(false)
        val ownId = room.ownUserId()
        val lastLine = runCatching {
            room.latestEvent().use { ev ->
                RoomLatestEventPreview.summarize(ev, ownId)
            }
        }.getOrNull()
        val unread = runCatching { unreadSnapshot(room) }.getOrDefault(UnreadSnapshot(0, 0))
        return RoomSummary(
            roomId = MatrixRoomId(room.id()),
            title = room.displayName() ?: room.id(),
            subtitle = lastLine?.text,
            avatarMxc = room.avatarUrl(),
            isEncrypted = isEncrypted,
            unreadCount = unread.unread,
            mentionCount = unread.mentions,
            lastActivityTimestampMs = lastLine?.timestampMs,
            presentationKind = when {
                isDm -> RoomPresentationKind.DirectMessage
                else -> RoomPresentationKind.Group
            },
            isSpace = room.isSpace(),
        )
    }

    private data class UnreadSnapshot(val unread: Int, val mentions: Int)

    /**
     * @param ignoreAtOrAbove after this device marks the room read, ignore SDK
     * counts that are still the pre-read backlog until the SDK drops below it.
     */
    private data class UnreadHold(
        val watermark: Int,
        val mentions: Int,
        val ignoreAtOrAbove: Int?,
    )

    private fun holdUnread(roomId: String, unread: Int, mentions: Int): UnreadSnapshot {
        ensureUnreadHoldsLoaded()
        val before = unreadHolds[roomId]
        val next = unreadHolds.compute(roomId) { _, existing ->
            val ignore = existing?.ignoreAtOrAbove
            when {
                // A drop from 70 to 1 is the one-event window rebuilding, not the
                // read receipt landing. Keep the badge clear until the count hits 0.
                ignore != null && unread > 0 -> existing.copy(watermark = 0, mentions = 0)
                ignore != null -> UnreadHold(0, 0, null)
                // A transient 0 from that same window must not wipe a backlog
                // we have already counted. Opening the room is what clears it.
                unread == 0 -> existing ?: UnreadHold(0, 0, null)
                else -> UnreadHold(
                    watermark = maxOf(existing?.watermark ?: 0, unread),
                    mentions = maxOf(existing?.mentions ?: 0, mentions),
                    ignoreAtOrAbove = null,
                )
            }
        } ?: UnreadHold(unread, mentions, null)
        if (
            next.watermark != before?.watermark ||
            next.mentions != before.mentions ||
            next.ignoreAtOrAbove != before.ignoreAtOrAbove
        ) {
            persistUnreadHolds()
        }
        return UnreadSnapshot(next.watermark, next.mentions)
    }

    private fun markRoomReadLocally(roomId: String) {
        ensureUnreadHoldsLoaded()
        unreadHolds.compute(roomId) { _, existing ->
            val floor = (existing?.watermark ?: 1).coerceAtLeast(1)
            UnreadHold(watermark = 0, mentions = 0, ignoreAtOrAbove = floor)
        }
        persistUnreadHolds()
    }

    private fun timelineLimitMarker(): File = File(appContext.filesDir, "matrix/active/timeline-limit-100")

    private fun unreadHoldsFile(): File = File(appContext.filesDir, "matrix/active/unread-counts.json")

    @Volatile
    private var unreadHoldsLoaded = false

    private fun ensureUnreadHoldsLoaded() {
        if (unreadHoldsLoaded) return
        synchronized(unreadHolds) {
            if (unreadHoldsLoaded) return
            val text = runCatching { unreadHoldsFile().readText() }.getOrNull()
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (json != null) {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val entry = json.optJSONObject(id) ?: continue
                    val unread = entry.optInt("u")
                    val mentions = entry.optInt("m")
                    if (unread > 0 || mentions > 0) {
                        unreadHolds[id] = UnreadHold(unread, mentions, null)
                    }
                }
            }
            unreadHoldsLoaded = true
        }
    }

    private fun persistUnreadHolds() {
        synchronized(unreadHolds) {
            val json = JSONObject()
            unreadHolds.forEach { (id, hold) ->
                val unread = if (hold.ignoreAtOrAbove != null) 0 else hold.watermark
                val mentions = if (hold.ignoreAtOrAbove != null) 0 else hold.mentions
                if (unread <= 0 && mentions <= 0) return@forEach
                json.put(id, JSONObject().put("u", unread).put("m", mentions))
            }
            runCatching {
                val file = unreadHoldsFile()
                file.parentFile?.mkdirs()
                file.writeText(json.toString())
            }
        }
    }

    private suspend fun unreadSnapshot(room: Room): UnreadSnapshot {
        val info = room.roomInfo()
        return try {
            // notificationCount / highlightCount are the homeserver's totals since the
            // read receipt. The numUnread* fields only count events this client has
            // loaded, and the room list loads one event, so those stay at 0 or 1
            // until history catches up.
            val serverNotifications = info.notificationCount.toLong()
            val serverMentions = info.highlightCount.toLong()
            val loadedMessages = info.numUnreadMessages.toLong()
            val loadedNotifications = info.numUnreadNotifications.toLong()
            val loadedMentions = info.numUnreadMentions.toLong()
            val mentions = maxOf(serverMentions, loadedMentions)
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()
            val base = maxOf(serverNotifications, loadedMessages, loadedNotifications, mentions.toLong())
            val unread = (if (info.isMarkedUnread && base == 0L) 1L else base)
                .coerceIn(0L, Int.MAX_VALUE.toLong())
                .toInt()
            holdUnread(room.id(), unread, mentions)
        } finally {
            runCatching { info.destroy() }
        }
    }

    private fun sortRoomSummaries(rooms: List<RoomSummary>): List<RoomSummary> =
        rooms.sortedWith(
            compareByDescending<RoomSummary> { it.lastActivityTimestampMs ?: 0L }
                .thenByDescending { it.title.lowercase() },
        )

    private fun newSessionPaths(): SessionPaths {
        val root = File(appContext.filesDir, "matrix/active").apply { mkdirs() }
        val data = File(root, "data").apply { mkdirs() }
        val cache = File(root, "cache").apply { mkdirs() }
        return SessionPaths(root = root, data = data, cache = cache)
    }

    private fun Throwable.isAlreadyAuthenticated(): Boolean =
        message?.contains("AlreadyInitialized", ignoreCase = true) == true

    private fun newBuilderForDiscovery(paths: SessionPaths): ClientBuilder =
        baseBuilder(paths).slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)

    private fun newBuilderForRestore(paths: SessionPaths, session: Session): ClientBuilder {
        val sliding = when (session.slidingSyncVersion) {
            SlidingSyncVersion.NATIVE -> SlidingSyncVersionBuilder.NATIVE
            SlidingSyncVersion.NONE -> SlidingSyncVersionBuilder.NONE
            else -> SlidingSyncVersionBuilder.NATIVE
        }
        return baseBuilder(paths).slidingSyncVersionBuilder(sliding)
    }

    private fun baseBuilder(paths: SessionPaths): ClientBuilder {
        val sqlite = SdkSqliteStoreBuilder(
            dataPath = paths.data.absolutePath,
            cachePath = paths.cache.absolutePath,
        ).journalSizeLimit(25u * 1024u * 1024u)
            .passphrase(null)

        return ClientBuilder()
            .sqliteStore(sqlite)
            .setSessionDelegate(delegate)
            .userAgent("Teletrix/0.1.0")
            .autoEnableBackups(true)
            .autoEnableCrossSigning(true)
            .enableShareHistoryOnInvite(true)
            .crossProcessLockConfig(CrossProcessLockConfig.SingleProcess)
            .threadsEnabled(true, false)
            .roomKeyRecipientStrategy(CollectStrategy.ERROR_ON_VERIFIED_USER_PROBLEM)
            .decryptionSettings(DecryptionSettings(TrustRequirement.UNTRUSTED))
            .requestConfig(
                RequestConfig(
                    timeout = 30_000uL,
                    retryLimit = 3u,
                    maxConcurrentRequests = null,
                    maxRetryTime = null,
                ),
            )
    }

    private val matrixMxidMention = Regex("@([A-Za-z0-9._=/-]+:[A-Za-z0-9.-]+)")
    private val matrixRoomMention = Regex("(^|\\s)@room(\\s|$)", RegexOption.IGNORE_CASE)

    private fun parseMentionUserIds(body: String): List<String> =
        matrixMxidMention.findAll(body).map { it.value }.distinct().toList()

    private fun bodyMentionsRoom(body: String): Boolean = matrixRoomMention.containsMatchIn(body)

    /** Turns each tagged "@name" into a `matrix.to` link so other clients render a pill. */
    private fun linkMentions(body: String, tags: List<MentionTag>): String {
        val usable = tags.filter { it.label.isNotBlank() }.distinctBy { it.label }
        if (usable.isEmpty()) return body
        val byLabel = usable.associateBy { it.label }
        val alternation = usable
            .sortedByDescending { it.label.length }
            .joinToString("|") { Regex.escape("@" + it.label) }
        val rx = Regex("(?<![\\w\\[])($alternation)(?=\\z|[\\s.,!?:;)])")
        return rx.replace(body) { m ->
            val tag = byLabel[m.value.removePrefix("@")] ?: return@replace m.value
            val shown = m.value.replace(Regex("([\\\\\\[\\]*_`])"), "\\\\$1")
            "[$shown](https://matrix.to/#/${tag.userId})"
        }
    }

    private fun disposeSessionVerificationController() {
        sasStarted.set(false)
        outgoingVerification.set(false)
        requestAccepted.set(false)
        userConfirmedMatch.set(false)
        approveInFlight.set(false)
        sasStartInFlight.set(false)
        pendingIncomingRequest = null
        val ctrl = sessionVerificationController
        sessionVerificationController = null
        runCatching { ctrl?.close() }
    }

    /**
     * Accept an incoming request. The device that asked for verification sends
     * the SAS start. If it does not, [didAcceptVerificationRequest] starts it.
     */
    private suspend fun acceptIncomingVerification() {
        val ctrl = sessionVerificationController ?: return
        val incoming = pendingIncomingRequest ?: return
        withContext(dispatchers.io + NonCancellable) {
            val (userId, flowId) = incoming
            runCatching { ctrl.acknowledgeVerificationRequest(userId, flowId) }
                .onFailure { Log.e(VERIFY_LOG, "acknowledge failed", it) }
            runCatching { ctrl.acceptVerificationRequest() }
                .onSuccess {
                    pendingIncomingRequest = null
                    requestAccepted.set(true)
                    Log.i(VERIFY_LOG, "incoming request accepted")
                }
                .onFailure { t ->
                    Log.e(VERIFY_LOG, "accept incoming failed", t)
                    if (sasStarted.get() ||
                        _sessionVerificationPrompt.value?.phase == SessionVerificationPhase.SasCompare
                    ) {
                        return@onFailure
                    }
                    _sessionVerificationPrompt.value =
                        SessionVerificationPrompt(
                            phase = SessionVerificationPhase.Failed,
                            message = t.message?.takeIf { it.isNotBlank() }
                                ?: "Could not accept the verification request.",
                        )
                }
        }
    }

    /**
     * Send the SAS start once the request is ready, unless the other device already did.
     * [approveVerification] is only for the emoji match, never for this step.
     */
    private suspend fun startSasOnce() {
        if (!requestAccepted.get() || sasStarted.get()) return
        if (!sasStartInFlight.compareAndSet(false, true)) return
        val ctrl = sessionVerificationController ?: run {
            sasStartInFlight.set(false)
            return
        }
        try {
            withContext(dispatchers.io + NonCancellable) {
                if (sasStarted.get()) return@withContext
                Log.i(VERIFY_LOG, "startSasVerification")
                runCatching { ctrl.startSasVerification() }
                    .onFailure { t ->
                        Log.e(VERIFY_LOG, "startSasVerification failed", t)
                        if (sasStarted.get() ||
                            _sessionVerificationPrompt.value?.phase == SessionVerificationPhase.SasCompare
                        ) {
                            return@onFailure
                        }
                        _sessionVerificationPrompt.value =
                            SessionVerificationPrompt(
                                phase = SessionVerificationPhase.Failed,
                                message = t.message?.takeIf { it.isNotBlank() }
                                    ?: "Could not start the emoji check.",
                            )
                    }
            }
        } finally {
            sasStartInFlight.set(false)
        }
    }

    /** didFinish is the MAC result. Also notice when the SDK marks the device verified. */
    private fun watchUntilVerified(generation: Int) {
        scope.launch(dispatchers.io) {
            repeat(30) { tick ->
                delay(1_000)
                val phase = _sessionVerificationPrompt.value?.phase
                if (phase == SessionVerificationPhase.Finished ||
                    phase == SessionVerificationPhase.Failed
                ) {
                    return@launch
                }
                if (approveGeneration.get() != generation) return@launch
                if (_sessionVerificationPrompt.value?.message?.startsWith("Confirming") == true) {
                    if (tick == 20) {
                        Log.w(VERIFY_LOG, "approve still in flight after 20s")
                        approveGeneration.incrementAndGet()
                        approveInFlight.set(false)
                        _sessionVerificationPrompt.update { current ->
                            if (current?.phase == SessionVerificationPhase.SasCompare) {
                                current.copy(
                                    message = "Sending the match is taking too long. Tap Match again, and confirm the same emoji on your other device.",
                                )
                            } else {
                                current
                            }
                        }
                        return@launch
                    }
                    return@repeat
                }
                val verified = runCatching {
                    val c = mutex.withLock { client } ?: return@runCatching false
                    c.encryption().use { enc ->
                        enc.verificationState() == VerificationState.VERIFIED
                    }
                }.getOrDefault(false)
                if (verified) {
                    Log.i(VERIFY_LOG, "verificationState VERIFIED")
                    sasStarted.set(false)
                    requestAccepted.set(false)
                    pendingIncomingRequest = null
                    _sessionVerificationPrompt.value =
                        SessionVerificationPrompt(
                            phase = SessionVerificationPhase.Finished,
                            message = "Session verified. Messages should show as verified on other clients.",
                        )
                    return@launch
                }
            }
        }
    }

    private suspend fun startOutboundSessionVerification(client: Client) {
        sasStarted.set(false)
        outgoingVerification.set(true)
        requestAccepted.set(false)
        userConfirmedMatch.set(false)
        pendingIncomingRequest = null
        val ctrl = ensureSessionVerificationController(client)
        _sessionVerificationPrompt.value =
            SessionVerificationPrompt(
                phase = SessionVerificationPhase.Pending,
                message = "Authorize this device: open Element or another signed-in Matrix client and accept the verification request, then compare emoji.",
            )
        try {
            Log.i(VERIFY_LOG, "requestDeviceVerification")
            try {
                ctrl.requestDeviceVerification()
            } catch (first: Throwable) {
                val stuck = first.message?.contains("another verification", ignoreCase = true) == true
                if (!stuck) throw first
                Log.w(VERIFY_LOG, "clearing a stuck verification so the other device can be prompted")
                suppressCancelUntilMs.set(System.currentTimeMillis() + 2_000)
                runCatching { ctrl.cancelVerification() }
                ctrl.requestDeviceVerification()
            }
            Log.i(VERIFY_LOG, "requestDeviceVerification sent")
        } catch (t: Throwable) {
            Log.e(VERIFY_LOG, "requestDeviceVerification failed", t)
            _sessionVerificationPrompt.value =
                SessionVerificationPrompt(
                    phase = SessionVerificationPhase.Failed,
                    message = t.message?.takeIf { it.isNotBlank() }
                        ?: "Could not request verification.",
                )
        }
    }

    private suspend fun ensureSessionVerificationController(client: Client): SessionVerificationController {
        sessionVerificationController?.let { return it }
        val created = client.getSessionVerificationController()
        created.setDelegate(verificationDelegate)
        sessionVerificationController = created
        return created
    }

    private suspend fun loadRoomMembersFor(room: Room): List<RoomMemberBrief> {
        val iter = room.members()
        return try {
            val out = ArrayList<RoomMemberBrief>(64)
            while (true) {
                val chunk = iter.nextChunk(80u) ?: break
                if (chunk.isEmpty()) break
                chunk.forEach { m ->
                    if (m.membership is MembershipState.Join) {
                        out += RoomMemberBrief(
                            userId = m.userId,
                            displayName = m.displayName?.trim().orEmpty(),
                            avatarMxc = m.avatarUrl?.trim()?.takeIf { it.isNotEmpty() },
                        )
                    }
                }
            }
            out.distinctBy { it.userId }.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { m ->
                    m.displayName.ifBlank { m.userId.substringAfter('@').substringBefore(':') }
                },
            )
        } finally {
            runCatching { iter.close() }
        }
    }

    private suspend fun sendImageAttachment(
        timeline: Timeline,
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ) {
        val uploadSource = UploadSource.Data(bytes, fileName)
        val params = UploadParameters(uploadSource, "", null, Mentions(emptyList(), false), null)
        val info = ImageInfo(
            0UL,
            0UL,
            mime,
            bytes.size.toULong(),
            null,
            null,
            null,
            null,
        )
        val handle = timeline.sendImage(params, uploadSource, info)
        try {
            handle.join()
        } finally {
            runCatching { handle.close() }
            runCatching { info.destroy() }
        }
    }

    private suspend fun sendFileAttachment(
        timeline: Timeline,
        bytes: ByteArray,
        fileName: String,
        mime: String,
    ) {
        val uploadSource = UploadSource.Data(bytes, fileName)
        val params = UploadParameters(uploadSource, "", null, Mentions(emptyList(), false), null)
        val finfo = FileInfo(mime, bytes.size.toULong(), null, null)
        val handle = timeline.sendFile(params, finfo)
        try {
            handle.join()
        } finally {
            runCatching { handle.close() }
            runCatching { finfo.destroy() }
        }
    }

    private data class SessionPaths(
        val root: File,
        val data: File,
        val cache: File,
    )

    private fun mentionsOwnUser(raw: String?, ownUserId: String): Boolean {
        if (raw.isNullOrBlank() || ownUserId.isBlank()) return false
        return runCatching {
            val obj = JSONObject(raw)
            val content = obj.optJSONObject("content") ?: return@runCatching false
            val mentions = content.optJSONObject("m.mentions")
            if (mentions?.optBoolean("room") == true) return@runCatching true
            val users = mentions?.optJSONArray("user_ids")
            if (users != null) {
                for (i in 0 until users.length()) {
                    if (users.optString(i) == ownUserId) return@runCatching true
                }
            }
            val local = ownUserId.substringAfter("@").substringBefore(":")
            val body = content.optString("body")
            val formatted = content.optJSONObject("formatted_body")?.toString()
                ?: content.optString("formatted_body")
            ownUserId in body || "@$local" in body ||
                (!formatted.isNullOrBlank() && (ownUserId in formatted || "@$local" in formatted))
        }.getOrDefault(false)
    }

    private companion object {
        const val VERIFY_LOG = "NgVerify"
        const val REACTION_RULE_ID = "com.nextgenmatrix.app.reaction"

        /** How long to let the other client send its SAS start before we send ours. */
        const val SAS_START_GRACE_MS = 4_000L
    }
}

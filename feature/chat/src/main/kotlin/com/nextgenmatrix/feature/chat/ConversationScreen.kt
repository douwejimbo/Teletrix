@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.nextgenmatrix.feature.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.nextgenmatrix.core.model.RoomMemberBrief
import com.nextgenmatrix.core.model.TimelineContentKind
import com.nextgenmatrix.core.model.TimelineMessage
import com.nextgenmatrix.core.ui.theme.BubbleIncoming
import com.nextgenmatrix.feature.media.MediaViewer
import com.nextgenmatrix.core.ui.theme.BubbleOutgoing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.BreakIterator
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dayLabelFmt: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@OptIn(FlowPreview::class)
@Composable
fun ConversationScreen(
    roomId: String,
    messages: List<TimelineMessage>,
    roomMembers: List<RoomMemberBrief>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickAttachment: (ByteArray, String, String) -> Unit,
    onNeedOlderPage: (lastVisibleIndex: Int, totalCount: Int) -> Unit,
    media: MatrixChatMedia,
    scrollRestoreEventId: String?,
    onConsumeScrollRestore: () -> Unit,
    onPersistScrollAnchor: (anchorEventId: String) -> Unit,
    onReadReceiptForEvent: (eventId: String) -> Unit,
    /** Bumps after each outbound send so the list jumps to the newest row. */
    scrollToLatestSignal: Int = 0,
    jumpToEventId: String? = null,
    onConsumeJumpToEvent: () -> Unit = {},
    onJumpToReply: (String) -> Unit = {},
    pendingComposeReply: PendingComposeReply? = null,
    onClearComposeReply: () -> Unit = {},
    onReplyToMessage: (TimelineMessage) -> Unit = {},
    onSendSticker: (ByteArray, String, String) -> Unit = { _, _, _ -> },
    onMentionTagged: (RoomMemberBrief) -> Unit = {},
    onViewerPlaybackStarted: () -> Unit = {},
    onPlayMusic: (TimelineMessage) -> Unit = {},
    playingTrackId: String? = null,
    musicIsPlaying: Boolean = false,
    preparingTrackId: String? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val display = remember(messages) { messages.asReversed() }
    val scope = rememberCoroutineScope()
    val memberById = remember(roomMembers) { roomMembers.associateBy { it.userId } }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    var profileMember by remember(roomId) { mutableStateOf<RoomMemberBrief?>(null) }
    var actionTarget by remember(roomId) { mutableStateOf<TimelineMessage?>(null) }
    var viewer by remember(roomId) { mutableStateOf<ViewerState?>(null) }
    val displayState = rememberUpdatedState(display)

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { it }
            .collect {
                focusManager.clearFocus()
                keyboard?.hide()
            }
    }

    LaunchedEffect(roomId, listState) {
        snapshotFlow {
            val current = displayState.value
            if (current.isEmpty()) return@snapshotFlow null
            val vis = listState.layoutInfo.visibleItemsInfo
            if (vis.isEmpty()) return@snapshotFlow null
            val anchorIdx = vis.minOf { it.index }
            current.getOrNull(anchorIdx)?.eventId?.value?.takeIf { !it.startsWith("txn:") }
        }
            .debounce(550)
            .distinctUntilChanged()
            .collect { id ->
                if (id != null) onReadReceiptForEvent(id)
            }
    }

    var allowScrollPersist by remember(roomId) { mutableStateOf(false) }
    var highlightEventId by remember(roomId) { mutableStateOf<String?>(null) }
    var unseenBelow by remember(roomId) { mutableStateOf(0) }

    LaunchedEffect(roomId) {
        allowScrollPersist = false
        delay(140)
        allowScrollPersist = true
    }

    LaunchedEffect(scrollRestoreEventId, roomId) {
        val anchor = scrollRestoreEventId ?: return@LaunchedEffect
        val current = displayState.value
        if (current.isEmpty()) return@LaunchedEffect
        val idx = current.indexOfFirst { it.eventId.value == anchor }
        if (idx >= 0) listState.scrollToItem(idx, 0)
        onConsumeScrollRestore()
    }

    LaunchedEffect(scrollToLatestSignal, listState) {
        if (scrollToLatestSignal <= 0) return@LaunchedEffect
        listState.scrollToItem(0, 0)
    }

    // reverseLayout puts new rows at index 0. Keep the same message on screen
    // unless the user is already sitting on the latest one.
    LaunchedEffect(roomId, listState) {
        var prevHeadId: String? = null
        var prevSize = 0
        var prevKey: String? = null
        var prevOffset = 0
        snapshotFlow {
            val current = displayState.value
            val first = listState.firstVisibleItemIndex
            HeadSizeOffset(
                headId = current.firstOrNull()?.eventId?.value,
                size = current.size,
                firstIndex = first,
                firstOffset = listState.firstVisibleItemScrollOffset,
                visibleKey = current.getOrNull(first)?.eventId?.value,
            )
        }.collect { state ->
            val pinned = state.firstIndex == 0 && state.firstOffset <= 96
            val added = state.size - prevSize
            val grewAtHead = added > 0 && state.headId != null && state.headId != prevHeadId
            if (prevHeadId != null && grewAtHead) {
                if (pinned) {
                    unseenBelow = 0
                } else {
                    val keep = prevKey
                    val idx = displayState.value.indexOfFirst { it.eventId.value == keep }
                    if (idx >= 0) {
                        listState.scrollToItem(idx, prevOffset)
                    }
                    unseenBelow += added
                }
            }
            if (pinned) unseenBelow = 0
            prevHeadId = state.headId
            prevSize = state.size
            prevKey = state.visibleKey
            prevOffset = state.firstOffset
        }
    }

    LaunchedEffect(jumpToEventId, messages) {
        val target = jumpToEventId ?: return@LaunchedEffect
        val idx = displayState.value.indexOfFirst { it.eventId.value == target }
        if (idx < 0) return@LaunchedEffect
        listState.animateScrollToItem(idx, 0)
        highlightEventId = target
        onConsumeJumpToEvent()
        delay(1600)
        if (highlightEventId == target) highlightEventId = null
    }

    LaunchedEffect(roomId, allowScrollPersist, listState) {
        if (!allowScrollPersist) return@LaunchedEffect
        snapshotFlow {
            val current = displayState.value
            if (current.isEmpty()) return@snapshotFlow null
            val i = listState.firstVisibleItemIndex
            current.getOrNull(i)?.eventId?.value
        }
            .distinctUntilChanged()
            .debounce(450)
            .collect { id ->
                if (id != null) onPersistScrollAnchor(id)
            }
    }

    val mentionTarget = remember(messages) {
        messages.filter { it.mentionsMe && !it.isOwn }.maxByOrNull { it.timestampMs }
    }
    var dismissedMentionIds by remember(roomId) { mutableStateOf<Set<String>>(emptySet()) }
    val pendingMention = mentionTarget?.takeIf { it.eventId.value !in dismissedMentionIds }
    val mentionIndex = remember(pendingMention, display) {
        pendingMention?.let { m -> display.indexOfFirst { it.eventId.value == m.eventId.value } } ?: -1
    }
    val mentionVisible = mentionIndex >= 0 &&
        listState.layoutInfo.visibleItemsInfo.any { it.index == mentionIndex }

    LaunchedEffect(roomId, display.size, listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= 0 && display.isNotEmpty() && lastVisible >= display.size - 4) {
                    onNeedOlderPage(lastVisible, display.size)
                }
            }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(chatBackdropBrush()),
    ) {
        ChatAmbientLayer()
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.Top,
                    reverseLayout = true,
                ) {
                    itemsIndexed(
                        items = display,
                        key = { _, m -> m.eventId.value + (m.transactionId ?: "") },
                    ) { index, msg ->
                        val older = display.getOrNull(index + 1)
                        val newer = display.getOrNull(index - 1)
                        val sameAsOlder = older != null &&
                            older.senderId == msg.senderId &&
                            older.isOwn == msg.isOwn &&
                            sameDay(older.timestampMs, msg.timestampMs)
                        val sameAsNewer = newer != null &&
                            newer.senderId == msg.senderId &&
                            newer.isOwn == msg.isOwn &&
                            sameDay(newer.timestampMs, msg.timestampMs)
                        val gapAfter = if (sameAsOlder) 2.dp else 10.dp

                        val needsDayChip = older == null || !sameDay(older.timestampMs, msg.timestampMs)

                        MessageRow(
                            message = msg,
                            media = media,
                            memberById = memberById,
                            isFirstInGroup = !sameAsNewer,
                            isLastInGroup = !sameAsOlder,
                            onReplySwipe = onReplyToMessage,
                            onJumpToReply = onJumpToReply,
                            highlighted = highlightEventId == msg.eventId.value,
                            onOpenProfile = { profileMember = it },
                            onLongPress = { actionTarget = it },
                            onOpenMedia = { tapped ->
                                viewer = viewerStateFor(tapped, messages)
                            },
                            onPlayMusic = onPlayMusic,
                            playingTrackId = playingTrackId,
                            musicIsPlaying = musicIsPlaying,
                            preparingTrackId = preparingTrackId,
                            modifier = Modifier.padding(bottom = gapAfter),
                        )
                        if (needsDayChip) {
                            DayDivider(timestampMs = msg.timestampMs)
                        }
                    }
                }

                val showJumpBottom by remember {
                    derivedStateOf {
                        unseenBelow > 0 ||
                            listState.firstVisibleItemIndex > 3 ||
                            listState.firstVisibleItemScrollOffset > 600
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showJumpBottom,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 8.dp),
                ) {
                    BadgedBox(
                        badge = {
                            if (unseenBelow > 0) {
                                Badge { Text(if (unseenBelow > 99) "99+" else unseenBelow.toString()) }
                            }
                        },
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                unseenBelow = 0
                                scope.launch {
                                    listState.animateScrollToItem(0, 0)
                                    keyboard?.hide()
                                    focusManager.clearFocus()
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Scroll to latest")
                        }
                    }
                }

                if (pendingMention != null && mentionIndex >= 0 && !mentionVisible) {
                    FilledTonalButton(
                        onClick = {
                            val mid = pendingMention.eventId.value
                            scope.launch {
                                listState.scrollToItem(mentionIndex, 0)
                                dismissedMentionIds = dismissedMentionIds + mid
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .fillMaxWidth(0.92f),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Outlined.Notifications, contentDescription = null)
                            Text("You were mentioned — view")
                        }
                    }
                }
            }

            ComposerBar(
                draft = draft,
                roomMembers = roomMembers,
                loadAvatar = media.loadAvatar,
                onMentionTagged = onMentionTagged,
                pendingComposeReply = pendingComposeReply,
                onClearComposeReply = onClearComposeReply,
                onDraftChange = onDraftChange,
                onSend = onSend,
                onPickAttachment = onPickAttachment,
                onSendSticker = onSendSticker,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
            )
        }

        profileMember?.let { m ->
            MemberProfileAlert(
                member = m,
                media = media,
                onDismiss = { profileMember = null },
            )
        }

        viewer?.let { v ->
            MediaViewer(
                items = v.items,
                startIndex = v.index,
                senderLabel = { m ->
                    if (m.isOwn) "You" else displayNameFor(m.senderId.value, m.senderDisplayName, memberById)
                },
                loadContent = media.loadContent,
                onPlaybackStarted = onViewerPlaybackStarted,
                onDismiss = { viewer = null },
            )
        }

        actionTarget?.let { target ->
            MessageActionSheet(
                message = target,
                memberById = memberById,
                onDismiss = { actionTarget = null },
                onReply = {
                    actionTarget = null
                    onReplyToMessage(target)
                },
                onOpenProfile = {
                    val brief = memberBriefForMessage(target, memberById)
                    actionTarget = null
                    profileMember = brief
                },
                onCopy = {
                    val text = target.body
                    if (text.isNotBlank()) {
                        clipboard.setText(AnnotatedString(text))
                    }
                    actionTarget = null
                },
            )
        }
    }
}

@Composable
private fun ChatAmbientLayer() {
    val pulse = rememberInfiniteTransition(label = "chat-ambient")
    val x1 by pulse.animateFloat(
        initialValue = -140f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = tween(8200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ambient-x1",
    )
    val y2 by pulse.animateFloat(
        initialValue = 220f,
        targetValue = -180f,
        animationSpec = infiniteRepeatable(
            animation = tween(9200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ambient-y2",
    )
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .graphicsLayer {
                    translationX = x1
                    translationY = -120f
                    alpha = 0.16f
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF4CCBFF), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .graphicsLayer {
                    translationX = -80f
                    translationY = y2
                    alpha = 0.12f
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF8D67FF), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )
    }
}

private data class HeadSizeOffset(
    val headId: String?,
    val size: Int,
    val firstIndex: Int,
    val firstOffset: Int,
    val visibleKey: String? = null,
)

@Composable
private fun chatBackdropBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.35f
    return if (dark) {
        Brush.verticalGradient(
            0f to Color(0xFF0E1318),
            0.5f to Color(0xFF0A0D10),
            1f to Color(0xFF06080A),
        )
    } else {
        Brush.verticalGradient(
            0f to Color(0xFFEAF1F8),
            0.6f to Color(0xFFD8E2EC),
            1f to Color(0xFFC9D5E1),
        )
    }
}

@Composable
private fun DayDivider(timestampMs: Long) {
    val label = remember(timestampMs) {
        val today = LocalDate.now()
        val date = Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()).toLocalDate()
        when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            else -> dayLabelFmt.format(date)
        }
    }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.35f
    val pill = if (dark) Color(0xFF1A2026).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f)
    val onPill = if (dark) Color(0xFFCBD3DA) else Color(0xFF3C4955)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = pill,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = onPill,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

private fun sameDay(aMs: Long, bMs: Long): Boolean {
    val zone = ZoneId.systemDefault()
    val a = Instant.ofEpochMilli(aMs).atZone(zone).toLocalDate()
    val b = Instant.ofEpochMilli(bMs).atZone(zone).toLocalDate()
    return a == b
}

@Composable
private fun ReplyQuoteStrip(
    message: TimelineMessage,
    memberById: Map<String, RoomMemberBrief>,
    bubbleColor: Color,
    bodyColor: Color,
    metaColor: Color,
    onJumpToReply: (String) -> Unit = {},
) {
    if (message.replyToEventId == null) return
    val accent = if (message.replyToParentIsOwn) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val authorLabel = when {
        message.replyToParentIsOwn -> "You"
        message.replyToSenderId != null -> displayNameFor(
            message.replyToSenderId!!,
            message.replyToPreviewAuthor?.takeIf { it != "…" },
            memberById,
        )
        !message.replyToPreviewAuthor.isNullOrBlank() -> message.replyToPreviewAuthor!!
        else -> "Message"
    }
    val preview = message.replyToPreviewBody?.takeIf { it.isNotBlank() } ?: "…"
    val quoteBackground = bubbleColor.luminance().let { lum ->
        if (lum > 0.5f) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.08f)
    }
    val targetId = message.replyToEventId?.value
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = quoteBackground,
        modifier = Modifier
            .padding(bottom = 6.dp)
            .then(
                if (targetId.isNullOrBlank() || targetId.startsWith("txn:")) {
                    Modifier
                } else {
                    Modifier.clickable { onJumpToReply(targetId) }
                },
            ),
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    text = authorLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = bodyColor.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun memberBriefForMessage(
    message: TimelineMessage,
    memberById: Map<String, RoomMemberBrief>,
): RoomMemberBrief {
    val id = message.senderId.value
    val fallbackName =
        message.senderDisplayName?.takeIf { it.isNotBlank() }
            ?: id.substringAfter("@").substringBefore(":").ifBlank { id }
    return memberById[id] ?: RoomMemberBrief(
        userId = id,
        displayName = fallbackName,
        avatarMxc = message.senderAvatarMxc,
    )
}

@Composable
private fun MemberProfileAlert(
    member: RoomMemberBrief,
    media: MatrixChatMedia,
    onDismiss: () -> Unit,
) {
    val title = member.displayName.ifBlank {
        member.userId.substringAfter("@").substringBefore(":").ifBlank { member.userId }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SenderAvatar(
                    mxc = member.avatarMxc,
                    hint = title,
                    loadAvatar = media.loadAvatar,
                    size = 88.dp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = member.userId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun MessageActionSheet(
    message: TimelineMessage,
    memberById: Map<String, RoomMemberBrief>,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onOpenProfile: () -> Unit,
    onCopy: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val brief = remember(message.eventId, memberById) {
        memberBriefForMessage(message, memberById)
    }
    val authorLine = if (message.isOwn) "You" else brief.displayName.ifBlank { brief.userId }
    val canReply = !message.eventId.value.startsWith("txn:")
    val canCopy = message.body.isNotBlank() &&
        message.contentKind != TimelineContentKind.Image &&
        message.contentKind != TimelineContentKind.Sticker

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    authorLine,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (message.body.isNotBlank()) {
                    Text(
                        message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (canReply) {
                ActionRow(
                    icon = Icons.AutoMirrored.Outlined.Reply,
                    label = "Reply",
                    onClick = onReply,
                )
            }
            ActionRow(
                icon = Icons.Outlined.AccountCircle,
                label = if (message.isOwn) "Your profile" else "View profile",
                onClick = onOpenProfile,
            )
            if (canCopy) {
                ActionRow(
                    icon = Icons.Outlined.ContentCopy,
                    label = "Copy text",
                    onClick = onCopy,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MessageRow(
    message: TimelineMessage,
    media: MatrixChatMedia,
    memberById: Map<String, RoomMemberBrief>,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    onReplySwipe: (TimelineMessage) -> Unit,
    onJumpToReply: (String) -> Unit,
    highlighted: Boolean,
    onOpenProfile: (RoomMemberBrief) -> Unit,
    onLongPress: (TimelineMessage) -> Unit,
    onOpenMedia: (TimelineMessage) -> Unit,
    onPlayMusic: (TimelineMessage) -> Unit,
    playingTrackId: String?,
    musicIsPlaying: Boolean,
    preparingTrackId: String?,
    modifier: Modifier = Modifier,
) {
    val isOwn = message.isOwn
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val triggerPx = with(density) { 64.dp.toPx() }
    val maxPx = with(density) { 96.dp.toPx() }
    val latestReply = rememberUpdatedState(onReplySwipe)
    val latestLongPress = rememberUpdatedState(onLongPress)

    val dragOffset = remember(message.eventId) { Animatable(0f) }
    val brief = remember(
        message.eventId,
        message.senderId,
        message.senderDisplayName,
        message.senderAvatarMxc,
        memberById,
    ) { memberBriefForMessage(message, memberById) }

    val senderName = remember(message.senderId, message.senderDisplayName, memberById) {
        displayNameFor(message.senderId.value, message.senderDisplayName, memberById)
    }
    val senderAvatarMxc = message.senderAvatarMxc ?: brief.avatarMxc

    val shape = bubbleShape(isOwn, isFirstInGroup, isLastInGroup)
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.35f

    val bubbleColor = when {
        isOwn -> BubbleOutgoing
        isDark -> BubbleIncoming
        else -> Color(0xFFFFFFFF)
    }
    val outgoingBrush = remember(scheme) {
        Brush.linearGradient(
            colors = listOf(
                BubbleOutgoing,
                scheme.primary.copy(alpha = 0.95f),
                scheme.tertiary.copy(alpha = 0.86f),
            ),
        )
    }
    val bodyColor = when {
        isOwn -> Color.White
        isDark -> Color(0xFFE8E8E8)
        else -> Color(0xFF1A1A1A)
    }
    val metaColor = when {
        isOwn -> Color.White.copy(alpha = 0.82f)
        isDark -> Color(0xFFA9B3BD)
        else -> Color(0xFF5C6B7A)
    }

    val time = remember(message.timestampMs) {
        runCatching {
            timeFmt.format(Instant.ofEpochMilli(message.timestampMs).atZone(ZoneId.systemDefault()))
        }.getOrDefault("")
    }

    val replyArrowAlpha by animateFloatAsState(
        targetValue = (dragOffset.value / triggerPx).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 60),
        label = "swipe-alpha",
    )
    val replyArrowScale by animateFloatAsState(
        targetValue = if (dragOffset.value >= triggerPx) 1.1f else 0.85f,
        animationSpec = tween(durationMillis = 90),
        label = "swipe-scale",
    )
    var entered by remember(message.eventId) { mutableStateOf(false) }
    LaunchedEffect(message.eventId) { entered = true }
    val appearAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "msg-appear-alpha",
    )
    val appearTranslateY by animateFloatAsState(
        targetValue = if (entered) 0f else 14f,
        animationSpec = tween(durationMillis = 260),
        label = "msg-appear-y",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
    ) {
        val maxBubble = maxWidth * 0.80f

        // Reveal a reply chip as the bubble is pulled toward the start of the row.
        Box(
            Modifier
                .matchParentSize()
                .padding(horizontal = 12.dp),
            contentAlignment = if (layoutDirection == LayoutDirection.Ltr) Alignment.CenterStart
            else Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .alpha(replyArrowAlpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size((20 * replyArrowScale).dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = appearAlpha
                    translationY = appearTranslateY
                }
                .offset {
                    val sign = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
                    IntOffset((sign * dragOffset.value).toInt(), 0)
                }
                .padding(horizontal = 2.dp),
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!isOwn) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .padding(end = 4.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (isFirstInGroup) {
                        SenderAvatar(
                            mxc = senderAvatarMxc,
                            hint = senderName,
                            loadAvatar = media.loadAvatar,
                            onClick = { onOpenProfile(brief) },
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start,
            ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .widthIn(max = maxBubble)
                    .pointerInput(message.eventId, layoutDirection) {
                        detectHorizontalDragGestures(
                            onDragStart = { },
                            onDragEnd = {
                                val triggered = dragOffset.value >= triggerPx &&
                                    !message.eventId.value.startsWith("txn:")
                                scope.launch {
                                    dragOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(durationMillis = 220),
                                    )
                                    if (triggered) {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        latestReply.value(message)
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch { dragOffset.animateTo(0f) }
                            },
                            onHorizontalDrag = { change, dx ->
                                val isLtr = layoutDirection == LayoutDirection.Ltr
                                val signed = if (isLtr) dx else -dx
                                val next = (dragOffset.value + signed).coerceIn(0f, maxPx)
                                if (next != dragOffset.value || signed > 0f) {
                                    scope.launch { dragOffset.snapTo(next) }
                                    if (signed > 0f) change.consume()
                                }
                            },
                        )
                    }
                    .pointerInput(message.eventId) {
                        detectTapGestures(
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                latestLongPress.value(message)
                            },
                        )
                    }
                    .shadow(
                        elevation = if (isOwn) 10.dp else 2.dp,
                        shape = shape,
                        clip = false,
                    )
                    .clip(shape)
                    .then(
                        if (isOwn) Modifier.background(outgoingBrush) else Modifier.background(bubbleColor),
                    )
                    .then(
                        if (highlighted) {
                            Modifier.border(2.dp, scheme.tertiary, shape)
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (isOwn) {
                            Modifier
                        } else {
                            Modifier.border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = if (isDark) 0.06f else 0.45f),
                                shape = shape,
                            )
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (!isOwn && isFirstInGroup) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = senderNameColor(message, scheme),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { onOpenProfile(brief) }
                            .padding(bottom = 4.dp),
                    )
                }
                ReplyQuoteStrip(
                    message = message,
                    memberById = memberById,
                    bubbleColor = bubbleColor,
                    bodyColor = bodyColor,
                    metaColor = metaColor,
                    onJumpToReply = onJumpToReply,
                )
                MessageBubbleBody(
                    message = message,
                    media = media,
                    memberById = memberById,
                    bodyColor = bodyColor,
                    metaColor = metaColor,
                    onOpenMedia = onOpenMedia,
                    onLongPress = onLongPress,
                    onPlayMusic = onPlayMusic,
                    playingTrackId = playingTrackId,
                    musicIsPlaying = musicIsPlaying,
                    preparingTrackId = preparingTrackId,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isOwn && message.readByUserIds.isEmpty() && !message.eventId.value.startsWith("txn:")) {
                        Text(
                            text = "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = metaColor,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = metaColor,
                    )
                }
            }
            ReadReceiptCluster(
                userIds = message.readByUserIds,
                memberById = memberById,
                loadAvatar = media.loadAvatar,
                onOpenProfile = onOpenProfile,
            )
            }

            if (isOwn) {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

/** Pick a stable, distinct color for an incoming sender's display name. */
private fun senderNameColor(
    message: TimelineMessage,
    scheme: androidx.compose.material3.ColorScheme,
): Color {
    val palette = listOf(
        scheme.primary,
        scheme.tertiary,
        Color(0xFFE57373),
        Color(0xFF81C784),
        Color(0xFFFFB74D),
        Color(0xFFBA68C8),
        Color(0xFF4DD0E1),
        Color(0xFFFFD54F),
    )
    val bucket = (message.senderId.value.hashCode().toLong() and Int.MAX_VALUE.toLong()).toInt() % palette.size
    return palette[bucket]
}

private const val ReceiptAvatarLimit = 4

@Composable
private fun ReadReceiptCluster(
    userIds: List<String>,
    memberById: Map<String, RoomMemberBrief>,
    loadAvatar: suspend (String?) -> ByteArray?,
    onOpenProfile: (RoomMemberBrief) -> Unit,
) {
    if (userIds.isEmpty()) return
    var showReaders by remember(userIds) { mutableStateOf(false) }
    val people = remember(userIds, memberById) {
        userIds.map { id ->
            memberById[id] ?: RoomMemberBrief(
                userId = id,
                displayName = id.substringAfter("@").substringBefore(":").ifBlank { id },
            )
        }
    }
    val scheme = MaterialTheme.colorScheme
    val overflow = people.size > ReceiptAvatarLimit
    Row(
        modifier = Modifier
            .padding(top = 3.dp, end = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { showReaders = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (overflow) {
            Text(
                text = "4+${people.size - ReceiptAvatarLimit} read",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(scheme.surfaceVariant.copy(alpha = 0.85f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        } else {
            val bubble = 18.dp
            val step = 12.dp
            Box(
                Modifier
                    .width(step * (people.size - 1) + bubble)
                    .height(bubble),
            ) {
                people.forEachIndexed { index, person ->
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = step * index)
                            .size(bubble)
                            .border(1.5.dp, scheme.background, CircleShape)
                            .padding(1.dp),
                    ) {
                        SenderAvatar(
                            mxc = person.avatarMxc,
                            hint = person.displayName.ifBlank { person.userId },
                            loadAvatar = loadAvatar,
                            size = 15.dp,
                        )
                    }
                }
            }
        }
    }
    if (showReaders) {
        AlertDialog(
            onDismissRequest = { showReaders = false },
            confirmButton = {
                TextButton(onClick = { showReaders = false }) { Text("Close") }
            },
            title = { Text(if (people.size == 1) "Read by" else "Read by ${people.size}") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    people.forEach { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    showReaders = false
                                    onOpenProfile(person)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SenderAvatar(
                                mxc = person.avatarMxc,
                                hint = person.displayName.ifBlank { person.userId },
                                loadAvatar = loadAvatar,
                                size = 36.dp,
                            )
                            Column {
                                Text(
                                    person.displayName.ifBlank { person.userId },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (person.displayName.isNotBlank()) {
                                    Text(
                                        person.userId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = scheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun SenderAvatar(
    mxc: String?,
    hint: String,
    loadAvatar: suspend (String?) -> ByteArray?,
    size: Dp = 32.dp,
    onClick: (() -> Unit)? = null,
) {
    val initial = remember(hint) {
        hint.firstOrNull { !it.isWhitespace() }?.uppercaseChar()?.toString() ?: "?"
    }
    val bitmap by produceState<ImageBitmap?>(null, mxc) {
        value = loadAvatar(mxc)?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    val seedColor = remember(hint) { avatarSeedColor(hint) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(seedColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = initial,
                style = when {
                    size >= 56.dp -> MaterialTheme.typography.titleLarge
                    size >= 40.dp -> MaterialTheme.typography.titleMedium
                    size >= 24.dp -> MaterialTheme.typography.labelLarge
                    else -> MaterialTheme.typography.labelSmall
                },
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun avatarSeedColor(hint: String): Color {
    val palette = listOf(
        Color(0xFF5E81F4),
        Color(0xFFEC6F8E),
        Color(0xFFF4A261),
        Color(0xFF2EBCAD),
        Color(0xFF9B59B6),
        Color(0xFF55C2DA),
        Color(0xFFE5C03A),
        Color(0xFF7CC576),
    )
    val hash = (hint.hashCode().toLong() and Int.MAX_VALUE.toLong()).toInt()
    return palette[hash % palette.size]
}

@Composable
private fun MessageTextWithPills(
    body: String,
    bodyColor: Color,
    metaColor: Color,
    memberById: Map<String, RoomMemberBrief>,
) {
    val annotated = remember(body, memberById, bodyColor) {
        mentionAnnotated(
            body = body,
            memberById = memberById,
            pillBackground = metaColor.copy(alpha = 0.18f),
            pillColor = bodyColor,
        )
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        color = bodyColor,
    )
}

@Composable
private fun MessageBubbleBody(
    message: TimelineMessage,
    media: MatrixChatMedia,
    memberById: Map<String, RoomMemberBrief>,
    bodyColor: Color,
    metaColor: Color,
    onOpenMedia: (TimelineMessage) -> Unit,
    onLongPress: (TimelineMessage) -> Unit,
    onPlayMusic: (TimelineMessage) -> Unit,
    playingTrackId: String?,
    musicIsPlaying: Boolean,
    preparingTrackId: String?,
) {
    val open = { onOpenMedia(message) }
    val longPress = { onLongPress(message) }
    when (message.contentKind) {
        TimelineContentKind.Image, TimelineContentKind.Sticker ->
            TimelineVisualMedia(message, media, bodyColor, onOpen = open, onLongPress = longPress)

        TimelineContentKind.Video ->
            MatrixVideoAttachment(message, media, bodyColor, metaColor, onOpen = open, onLongPress = longPress)

        TimelineContentKind.Audio ->
            MatrixAudioAttachment(
                message = message,
                media = media,
                bodyColor = bodyColor,
                metaColor = metaColor,
                onPlayMusic = onPlayMusic,
                isCurrentTrack = playingTrackId == message.eventId.value,
                musicIsPlaying = musicIsPlaying && playingTrackId == message.eventId.value,
                isPreparing = preparingTrackId == message.eventId.value,
                onOpen = open,
                onLongPress = longPress,
            )

        TimelineContentKind.File ->
            FileAttachmentRow(message, bodyColor, metaColor, onOpen = open, onLongPress = longPress)

        else ->
            MessageTextWithPills(
                body = message.body,
                bodyColor = bodyColor,
                metaColor = metaColor,
                memberById = memberById,
            )
    }
}

@Composable
private fun MatrixVideoAttachment(
    message: TimelineMessage,
    media: MatrixChatMedia,
    bodyColor: Color,
    metaColor: Color,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val thumbJson = message.mediaSourceJson
    val thumb by produceState<ImageBitmap?>(null, thumbJson, message.eventId) {
        value = thumbJson?.let { j ->
            media.loadThumbnail(j)?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
    }

    Column {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 240.dp)
                .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb!!,
                    contentDescription = message.body,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Outlined.Videocam, contentDescription = null, tint = metaColor)
            }
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        message.mediaDurationMs?.let { ms ->
            if (ms > 0) {
                Spacer(Modifier.height(4.dp))
                Text(formatDuration(ms), style = MaterialTheme.typography.labelSmall, color = metaColor)
            }
        }
        if (message.body.isNotBlank() && !message.body.equals("Video", ignoreCase = true)) {
            Spacer(Modifier.height(6.dp))
            Text(message.body, style = MaterialTheme.typography.bodyMedium, color = bodyColor)
        }
    }
}

@Composable
private fun MusicFileRow(
    message: TimelineMessage,
    bodyColor: Color,
    metaColor: Color,
    isPreparing: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val notifyPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { onPlay() }
    Row(
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Audiotrack,
            contentDescription = null,
            tint = metaColor,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = message.body.ifBlank { "Audio" },
                style = MaterialTheme.typography.bodyLarge,
                color = bodyColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            message.mediaDurationMs?.takeIf { it > 0 }?.let {
                Text(formatDuration(it), style = MaterialTheme.typography.labelSmall, color = metaColor)
            }
        }
        IconButton(
            onClick = {
                val needsNotification = Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsNotification) {
                    notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onPlay()
                }
            },
            colors = IconButtonDefaults.iconButtonColors(contentColor = bodyColor),
        ) {
            when {
                isPreparing ->
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = metaColor)
                isPlaying ->
                    Icon(Icons.Filled.Pause, contentDescription = "Pause")
                else ->
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
            }
        }
    }
}

@Composable
private fun MatrixAudioAttachment(
    message: TimelineMessage,
    media: MatrixChatMedia,
    bodyColor: Color,
    metaColor: Color,
    onPlayMusic: (TimelineMessage) -> Unit,
    isCurrentTrack: Boolean,
    musicIsPlaying: Boolean,
    isPreparing: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    if (!message.isVoiceMessage) {
        MusicFileRow(
            message = message,
            bodyColor = bodyColor,
            metaColor = metaColor,
            isPreparing = isPreparing,
            isPlaying = isCurrentTrack && musicIsPlaying,
            onPlay = { onPlayMusic(message) },
            onOpen = onOpen,
            onLongPress = onLongPress,
        )
        return
    }
    val src = message.mediaSourceJson
    if (src == null) {
        Text(message.body, style = MaterialTheme.typography.bodyLarge, color = bodyColor)
        return
    }
    val context = LocalContext.current
    val bytes by produceState<ByteArray?>(null, src, message.eventId) {
        value = media.loadContent(src)
    }

    val uri = remember(bytes) {
        bytes?.let { b ->
            val ext = if (message.isVoiceMessage) "ogg" else "audio"
            val f = File(context.cacheDir, "ax_${message.eventId.value}.$ext")
            f.writeBytes(b)
            Uri.fromFile(f)
        }
    }

    val player = remember(message.eventId) {
        ExoPlayer.Builder(context).build()
    }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        val u = uri ?: return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(u))
        player.prepare()
        player.playWhenReady = false
        playing = false
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Row(
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = metaColor,
            modifier = Modifier.size(22.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = if (message.isVoiceMessage) "Voice message" else message.body,
                style = MaterialTheme.typography.bodyLarge,
                color = bodyColor,
            )
            message.mediaDurationMs?.takeIf { it > 0 }?.let {
                Text(
                    formatDuration(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = metaColor,
                )
            }
        }
        IconButton(
            onClick = {
                val u = uri
                if (u == null) return@IconButton
                if (playing) player.pause() else {
                    player.playWhenReady = true
                    player.play()
                }
            },
            enabled = uri != null,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = bodyColor,
                disabledContentColor = metaColor.copy(alpha = 0.35f),
            ),
        ) {
            if (bytes == null) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = metaColor)
            } else if (playing) {
                Icon(Icons.Filled.Pause, contentDescription = "Pause")
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
            }
        }
    }
}

@Composable
private fun FileAttachmentRow(
    message: TimelineMessage,
    bodyColor: Color,
    metaColor: Color,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.InsertDriveFile,
            contentDescription = null,
            tint = metaColor,
            modifier = Modifier.size(22.dp),
        )
        Text(message.body, style = MaterialTheme.typography.bodyLarge, color = bodyColor)
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val r = s % 60
    return String.format("%d:%02d", m, r)
}

private fun bubbleShape(isOwn: Boolean, first: Boolean, last: Boolean): RoundedCornerShape {
    val outer = 20.dp
    val inner = 6.dp
    val mid = 14.dp
    return if (isOwn) {
        RoundedCornerShape(
            topStart = outer,
            topEnd = if (first) outer else inner,
            bottomStart = outer,
            bottomEnd = if (last) inner else inner,
        )
    } else {
        RoundedCornerShape(
            topStart = if (first) outer else inner,
            topEnd = outer,
            bottomStart = if (last) inner else inner,
            bottomEnd = outer,
        )
    }
}

@Composable
private fun ComposerBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    roomMembers: List<RoomMemberBrief>,
    loadAvatar: suspend (String?) -> ByteArray?,
    onMentionTagged: (RoomMemberBrief) -> Unit,
    pendingComposeReply: PendingComposeReply?,
    onClearComposeReply: () -> Unit,
    onPickAttachment: (ByteArray, String, String) -> Unit,
    onSendSticker: (ByteArray, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val canSend = draft.isNotBlank()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val fieldFocus = remember { FocusRequester() }

    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var panelTabId by rememberSaveable { mutableStateOf(StickerCatalog.packs.first().id) }
    var recents by remember { mutableStateOf(RecentStickers.load(context)) }
    var field by remember { mutableStateOf(TextFieldValue(draft, TextRange(draft.length))) }
    var mentionMenu by remember { mutableStateOf(false) }
    var mentionPick by remember { mutableStateOf<List<MentionOption>>(emptyList()) }
    var pickedLabels by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(draft) {
        if (field.text != draft) {
            field = TextFieldValue(draft, TextRange(draft.length))
        }
    }

    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val cr = context.contentResolver
            val mime = cr.getType(uri) ?: "application/octet-stream"
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
            val bytes = runCatching { cr.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                ?: return@launch
            onPickAttachment(bytes, name, mime)
        }
    }

    fun syncMentions(value: TextFieldValue) {
        val mq = mentionQueryAtCursor(value.text, value.selection.start)
        if (mq == null) {
            mentionMenu = false
            return
        }
        val q = mq.second.trim()
        fun labelOf(m: RoomMemberBrief) =
            m.displayName.ifBlank { m.userId.substringAfter('@').substringBefore(':') }
        // A space ends the name unless it is part of a longer name ("Max " -> "Max Power").
        if (mq.second.isNotEmpty() && mq.second.last().isWhitespace() &&
            roomMembers.none { labelOf(it).startsWith(mq.second, ignoreCase = true) }
        ) {
            mentionMenu = false
            return
        }
        val ranked = roomMembers
            .asSequence()
            .filter { m ->
                q.isEmpty() ||
                    labelOf(m).contains(q, ignoreCase = true) ||
                    m.userId.contains(q, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<RoomMemberBrief> { labelOf(it).startsWith(q, ignoreCase = true) }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { labelOf(it) },
            )
            .take(30)
            .map { MentionOption.Person(it, labelOf(it)) }
            .toList()
        val everyone = if ("room".startsWith(q.lowercase())) listOf(MentionOption.Everyone) else emptyList()
        mentionPick = everyone + ranked
        mentionMenu = mentionPick.isNotEmpty()
    }

    fun onTextChanged(value: TextFieldValue) {
        field = value
        onDraftChange(value.text)
        syncMentions(value)
        if (value.text.isBlank()) pickedLabels = emptySet()
    }

    fun applyMention(option: MentionOption) {
        val mq = mentionQueryAtCursor(field.text, field.selection.start) ?: return
        val atIdx = mq.first
        val cursor = field.selection.start.coerceIn(0, field.text.length)
        val label = when (option) {
            is MentionOption.Person -> option.label
            MentionOption.Everyone -> "room"
        }
        if (option is MentionOption.Person) {
            onMentionTagged(option.member)
            pickedLabels = pickedLabels + label
        }
        val inserted = "@$label "
        val next = field.text.substring(0, atIdx) + inserted + field.text.substring(cursor)
        onTextChanged(TextFieldValue(next, TextRange(atIdx + inserted.length)))
    }

    fun insertEmoji(emoji: String) {
        val start = field.selection.start.coerceIn(0, field.text.length)
        val end = field.selection.end.coerceIn(0, field.text.length)
        val from = minOf(start, end)
        val to = maxOf(start, end)
        val next = field.text.replaceRange(from, to, emoji)
        onTextChanged(TextFieldValue(next, TextRange(from + emoji.length)))
    }

    fun deleteBeforeCursor() {
        val start = field.selection.start.coerceIn(0, field.text.length)
        val end = field.selection.end.coerceIn(0, field.text.length)
        if (start != end) {
            val from = minOf(start, end)
            val to = maxOf(start, end)
            onTextChanged(TextFieldValue(field.text.removeRange(from, to), TextRange(from)))
            return
        }
        if (start == 0) return
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(field.text)
        val cut = iterator.preceding(start).let { if (it == BreakIterator.DONE) 0 else it }
        onTextChanged(TextFieldValue(field.text.removeRange(cut, start), TextRange(cut)))
    }

    fun sendBuiltIn(sticker: BuiltInSticker) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { stickerPayload(context, sticker) }
            recents = RecentStickers.remember(context, sticker)
            onSendSticker(bytes, "${sticker.emoji}.tgs", "application/gzip")
        }
    }

    val panelTab = when (panelTabId) {
        "emoji" -> StickerPanelTab.Emoji
        "recent" -> StickerPanelTab.Recent
        else -> StickerCatalog.packById(panelTabId)?.let { StickerPanelTab.Pack(it.id) }
            ?: StickerPanelTab.Pack(StickerCatalog.packs.first().id)
    }

    Column(modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = panelOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            StickerEmojiPanel(
                tab = panelTab,
                onTab = { next ->
                    panelTabId = when (next) {
                        StickerPanelTab.Emoji -> "emoji"
                        StickerPanelTab.Recent -> "recent"
                        is StickerPanelTab.Pack -> next.id
                    }
                },
                recents = recents,
                onSticker = ::sendBuiltIn,
                onEmoji = ::insertEmoji,
                onBackspace = ::deleteBeforeCursor,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            shape = RoundedCornerShape(28.dp),
            color = scheme.surface.copy(alpha = 0.87f),
            tonalElevation = 4.dp,
            shadowElevation = 18.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
            pendingComposeReply?.let { pr ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = scheme.surfaceContainerHigh.copy(alpha = 0.92f),
                    tonalElevation = 1.dp,
                ) {
                    Row(
                        Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(38.dp)
                                .background(scheme.primary, RoundedCornerShape(2.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${pr.authorLabel}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = scheme.primary,
                            )
                            Text(
                                text = pr.bodyPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = onClearComposeReply) {
                            Icon(Icons.Outlined.Close, contentDescription = "Cancel reply")
                        }
                    }
                }
            }
            if (mentionMenu) {
                Surface(
                    tonalElevation = 1.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    color = scheme.surfaceContainerHigh,
                ) {
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                    ) {
                        mentionPick.forEach { option ->
                            MentionOptionRow(
                                option = option,
                                loadAvatar = loadAvatar,
                                onClick = { applyMention(option) },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(
                    onClick = { pickFile.launch("*/*") },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = scheme.onSurfaceVariant),
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = "Attach file")
                }
                IconButton(
                    onClick = {
                        if (panelOpen) {
                            panelOpen = false
                            fieldFocus.requestFocus()
                            keyboard?.show()
                        } else {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            panelOpen = true
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = scheme.onSurfaceVariant),
                ) {
                    Icon(
                        if (panelOpen) Icons.Outlined.Keyboard else Icons.Outlined.Mood,
                        contentDescription = if (panelOpen) "Keyboard" else "Stickers and emoji",
                    )
                }
                OutlinedTextField(
                    value = field,
                    onValueChange = { onTextChanged(it) },
                    visualTransformation = remember(pickedLabels, scheme.primary) {
                        MentionHighlightTransformation(pickedLabels, scheme.primary)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp)
                        .focusRequester(fieldFocus)
                        .onFocusChanged { state ->
                            if (state.isFocused) panelOpen = false
                        },
                    placeholder = {
                        Text("Message", color = scheme.onSurfaceVariant.copy(alpha = 0.7f))
                    },
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = scheme.surfaceVariant.copy(alpha = 0.92f),
                        unfocusedContainerColor = scheme.surfaceVariant.copy(alpha = 0.8f),
                    ),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) {
                                onSend()
                                keyboard?.hide()
                                mentionMenu = false
                            }
                        },
                    ),
                )
                Box(
                    Modifier
                        .padding(start = 4.dp, end = 2.dp, bottom = 4.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) {
                                Brush.linearGradient(
                                    listOf(scheme.primary, scheme.tertiary.copy(alpha = 0.95f)),
                                )
                            } else {
                                Brush.linearGradient(
                                    listOf(scheme.surfaceVariant, scheme.surfaceVariant),
                                )
                            },
                        )
                        .clickable(enabled = canSend) {
                            onSend()
                            keyboard?.hide()
                            mentionMenu = false
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send",
                        tint = if (canSend) scheme.onPrimary else scheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
        }
    }
}

private sealed interface MentionOption {
    data class Person(val member: RoomMemberBrief, val label: String) : MentionOption
    data object Everyone : MentionOption
}

@Composable
private fun MentionOptionRow(
    option: MentionOption,
    loadAvatar: suspend (String?) -> ByteArray?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (option) {
            is MentionOption.Person -> {
                SenderAvatar(
                    mxc = option.member.avatarMxc,
                    hint = option.label,
                    loadAvatar = loadAvatar,
                    size = 36.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        option.member.userId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            MentionOption.Everyone -> {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("@", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text("@room", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Notify everyone in this room",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private class ViewerState(val items: List<TimelineMessage>, val index: Int)

/** Images, videos and stickers page together; audio and files open on their own. */
private fun viewerStateFor(tapped: TimelineMessage, all: List<TimelineMessage>): ViewerState {
    val visual = setOf(TimelineContentKind.Image, TimelineContentKind.Video, TimelineContentKind.Sticker)
    if (tapped.contentKind !in visual) return ViewerState(listOf(tapped), 0)
    val pages = all.filter { it.contentKind in visual }
    val index = pages.indexOfFirst { it.eventId == tapped.eventId }
    return if (index >= 0) ViewerState(pages, index) else ViewerState(listOf(tapped), 0)
}

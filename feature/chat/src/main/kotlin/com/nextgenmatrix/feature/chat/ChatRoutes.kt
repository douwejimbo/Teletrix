package com.nextgenmatrix.feature.chat

import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextgenmatrix.core.model.RoomPresentationKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class RoomListFilter {
    All,
    Unread,
    Direct,
    Groups,
}

private val chatListTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val chatListShortDayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE")
private val chatListShortDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")

@Composable
fun ChatListRoute(
    onOpenRoom: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(RoomListFilter.All) }
    val visible = remember(rooms, query, filter) {
        rooms.filter { room ->
            // Spaces have their own tab. A chat list entry would just be a second copy.
            if (room.isSpace) return@filter false
            val matchesQuery = query.isBlank() || room.title.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                RoomListFilter.All -> true
                RoomListFilter.Unread -> room.unreadCount > 0
                RoomListFilter.Direct -> room.presentationKind == RoomPresentationKind.DirectMessage
                RoomListFilter.Groups -> room.presentationKind != RoomPresentationKind.DirectMessage
            }
            matchesQuery && matchesFilter
        }
    }
    val media = remember(viewModel) {
        MatrixChatMedia(
            loadThumbnail = { _: String -> null },
            loadContent = { _: String -> null },
            loadAvatar = { mxc -> viewModel.loadRoomAvatar(mxc) },
        )
    }
    val navInsets = WindowInsets.navigationBars.asPaddingValues()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = 8.dp + navInsets.calculateBottomPadding(),
            start = 0.dp,
            end = 0.dp,
        ),
    ) {
        item(key = "premium_header") {
            ChatListHero(
                query = query,
                onQuery = { query = it },
                filter = filter,
                onFilter = { filter = it },
            )
        }
        if (visible.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = if (query.isBlank() && filter == RoomListFilter.All) "No chats yet" else "No matches",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }
        }
        items(visible, key = { it.roomId.value }) { room ->
            ChatRoomRow(
                title = room.title,
                subtitle = room.subtitle,
                avatarMxc = room.avatarMxc,
                unreadCount = room.unreadCount,
                mentionCount = room.mentionCount,
                lastActivityMs = room.lastActivityTimestampMs,
                loadAvatar = media.loadAvatar,
                onClick = { onOpenRoom(room.roomId.value) },
            )
        }
    }
}

@Composable
private fun ChatListHero(
    query: String,
    onQuery: (String) -> Unit,
    filter: RoomListFilter,
    onFilter: (RoomListFilter) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    var expanded by remember { mutableStateOf(query.isNotEmpty()) }
    var searchFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "chat-search",
    )
    LaunchedEffect(expanded) {
        if (expanded) focusRequester.requestFocus()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(44.dp)) {
            Text(
                text = "Chats",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSurface,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer { alpha = (1f - progress * 1.35f).coerceIn(0f, 1f) },
            )
            val barWidth = 40.dp + (maxWidth - 40.dp) * progress
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(barWidth)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(enabled = !expanded) { expanded = true },
                shape = RoundedCornerShape(20.dp),
                color = scheme.surfaceVariant.copy(alpha = 0.72f * progress),
            ) {
                Row(
                    Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (expanded) {
                        BasicTextField(
                            value = query,
                            onValueChange = onQuery,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp)
                                .focusRequester(focusRequester)
                                .onFocusChanged { state ->
                                    val wasFocused = searchFocused
                                    searchFocused = state.isFocused
                                    if (wasFocused && !state.isFocused && query.isBlank()) {
                                        expanded = false
                                        keyboard?.hide()
                                    }
                                },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                            cursorBrush = SolidColor(scheme.primary),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) {
                                        Text(
                                            "Search",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        if (query.isNotEmpty()) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Clear search",
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .size(18.dp)
                                    .clickable {
                                        onQuery("")
                                        focusRequester.requestFocus()
                                    },
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search chats",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = if (expanded) 10.dp else 0.dp)
                            .size(22.dp)
                            .clickable {
                                if (expanded && query.isBlank()) {
                                    expanded = false
                                    keyboard?.hide()
                                } else {
                                    expanded = true
                                }
                            },
                    )
                    if (!expanded) Spacer(Modifier.weight(1f))
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                RoomListFilter.All to "All",
                RoomListFilter.Unread to "Unread",
                RoomListFilter.Direct to "Direct",
                RoomListFilter.Groups to "Groups",
            ).forEach { (id, label) ->
                val selected = filter == id
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) scheme.primary else scheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.clickable { onFilter(id) },
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatRoomRow(
    title: String,
    subtitle: String?,
    avatarMxc: String?,
    unreadCount: Int,
    mentionCount: Int,
    lastActivityMs: Long?,
    loadAvatar: suspend (String?) -> ByteArray?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bitmap by produceState<ImageBitmap?>(null, avatarMxc) {
        value = loadAvatar(avatarMxc)?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    val seedColor = remember(title) { chatRoomAvatarSeed(title) }
    val initial = remember(title) {
        title.firstOrNull { !it.isWhitespace() }?.uppercaseChar()?.toString() ?: "#"
    }
    val timeLabel = remember(lastActivityMs) { formatLastActivity(lastActivityMs) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(seedColor, seedColor.copy(alpha = 0.7f)),
                        ),
                    ),
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
                        initial,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!timeLabel.isNullOrBlank()) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unreadCount > 0) scheme.primary else scheme.onSurfaceVariant,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        subtitle?.takeIf { it.isNotBlank() } ?: "Tap to open",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (unreadCount > 0) {
                        Spacer(Modifier.size(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (mentionCount > 0) scheme.tertiary else scheme.primary,
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = scheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            color = scheme.outlineVariant.copy(alpha = 0.45f),
        )
    }
}

private fun chatRoomAvatarSeed(title: String): Color {
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
    val hash = (title.hashCode().toLong() and Int.MAX_VALUE.toLong()).toInt()
    return palette[hash % palette.size]
}

private fun formatLastActivity(ms: Long?): String? {
    val timestamp = ms ?: return null
    if (timestamp <= 0L) return null
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(timestamp).atZone(zone)
    val today = LocalDate.now(zone)
    val msgDate = date.toLocalDate()
    return when {
        msgDate == today -> chatListTimeFmt.format(date)
        today.minusDays(1) == msgDate -> "Yesterday"
        today.minusDays(6).isBefore(msgDate) -> chatListShortDayFmt.format(date)
        else -> chatListShortDateFmt.format(date)
    }
}

@Composable
fun ConversationRoute(
    roomId: String,
    modifier: Modifier = Modifier,
    initialJumpEventId: String? = null,
    onJumpFromNotification: () -> Unit = {},
    viewModel: ConversationViewModel = hiltViewModel(),
) {
    LaunchedEffect(roomId) {
        viewModel.bindRoom(roomId)
        viewModel.loadDraft(roomId)
    }
    LaunchedEffect(roomId, initialJumpEventId) {
        val target = initialJumpEventId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        viewModel.jumpToReply(target)
        onJumpFromNotification()
    }
    val timeline by viewModel.messages.collectAsStateWithLifecycle()
    val items = timeline[roomId].orEmpty()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val scrollRestoreEventId by viewModel.scrollRestoreEventId.collectAsStateWithLifecycle()
    val scrollToLatestSignal by viewModel.scrollToLatestSignal.collectAsStateWithLifecycle()
    val jumpToEventId by viewModel.jumpToEventId.collectAsStateWithLifecycle()
    val pendingComposeReply by viewModel.pendingReply.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val preparingTrackId by viewModel.preparingTrackId.collectAsStateWithLifecycle()
    val membersMap by viewModel.roomMembersByRoom.collectAsStateWithLifecycle()
    val roomMembers = membersMap[roomId].orEmpty()

    val media = remember(viewModel) {
        MatrixChatMedia(
            loadThumbnail = { viewModel.loadMediaThumbnail(it) },
            loadContent = { viewModel.loadMediaContent(it) },
            loadAvatar = { viewModel.loadSenderAvatar(it) },
        )
    }

    ConversationScreen(
        roomId = roomId,
        messages = items,
        roomMembers = roomMembers,
        draft = draft,
        onDraftChange = viewModel::onDraftChange,
        onSend = {
            val text = draft.trim()
            if (text.isNotEmpty()) {
                viewModel.send(roomId, text)
                viewModel.onDraftChange("")
                viewModel.persistDraft(roomId)
            }
        },
        onPickAttachment = { bytes, name, mime ->
            viewModel.sendAttachment(roomId, bytes, name, mime)
        },
        onSendSticker = { bytes, name, mime ->
            viewModel.sendSticker(roomId, bytes, name, mime)
        },
        onMentionTagged = viewModel::onMentionPicked,
        onViewerPlaybackStarted = viewModel::pauseMusicForViewer,
        onPlayMusic = viewModel::onPlayChatAudio,
        playingTrackId = nowPlaying?.trackId,
        musicIsPlaying = nowPlaying?.isPlaying == true,
        preparingTrackId = preparingTrackId,
        onNeedOlderPage = { lastVisible, total ->
            viewModel.requestOlderMessagesIfNeeded(roomId, lastVisible, total)
        },
        media = media,
        scrollRestoreEventId = scrollRestoreEventId,
        onConsumeScrollRestore = viewModel::consumeScrollRestore,
        onPersistScrollAnchor = { eventId -> viewModel.persistScrollAnchor(roomId, eventId) },
        onReadReceiptForEvent = { eventId -> viewModel.updateRoomReadReceipts(roomId, eventId) },
        scrollToLatestSignal = scrollToLatestSignal,
        jumpToEventId = jumpToEventId,
        onConsumeJumpToEvent = viewModel::consumeJumpToEvent,
        onJumpToReply = viewModel::jumpToReply,
        pendingComposeReply = pendingComposeReply,
        onClearComposeReply = viewModel::clearComposeReply,
        onReplyToMessage = viewModel::setComposeReply,
        modifier = modifier,
    )
}

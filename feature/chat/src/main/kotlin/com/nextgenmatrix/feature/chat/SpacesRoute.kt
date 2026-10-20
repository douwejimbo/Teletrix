package com.nextgenmatrix.feature.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextgenmatrix.core.model.SpaceEntry

@Composable
fun SpacesRoute(
    onOpenSpace: (String) -> Unit,
    onBrowseDirectory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpacesViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.loadTopLevel() }
    val account by viewModel.account.collectAsStateWithLifecycle()
    val host = account?.homeserverUrl
        ?.removePrefix("https://")
        ?.removePrefix("http://")
        ?.trimEnd('/')
        .orEmpty()
    Column(modifier.fillMaxSize()) {
        BrowseServerRow(host = host, onClick = onBrowseDirectory)
        SpaceList(
            entries = viewModel.entries.collectAsStateWithLifecycle().value,
            loading = viewModel.loading.collectAsStateWithLifecycle().value,
            emptyTitle = "No spaces yet",
            emptyBody = "Spaces you join are listed here. The directory above shows everything this server publishes.",
            loadAvatar = viewModel::loadAvatar,
            onClick = { entry ->
                if (entry.isSpace) onOpenSpace(entry.roomId)
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun SpaceDetailRoute(
    spaceId: String,
    onOpenRoom: (String) -> Unit,
    onOpenSpace: (String) -> Unit,
    onTitle: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpacesViewModel = hiltViewModel(),
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(spaceId, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.loadSpace(spaceId)
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.loadSpace(spaceId)
        }
        onDispose { lifecycle.removeObserver(observer) }
    }
    val title by viewModel.title.collectAsStateWithLifecycle()
    LaunchedEffect(title) { title?.let(onTitle) }
    val joiningId by viewModel.joiningId.collectAsStateWithLifecycle()
    val joinError by viewModel.joinError.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        joinError?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        SpaceList(
            entries = viewModel.entries.collectAsStateWithLifecycle().value,
            loading = viewModel.loading.collectAsStateWithLifecycle().value,
            joiningId = joiningId,
            emptyTitle = "Nothing in this space",
            emptyBody = "Rooms and spaces added to it will be listed here, including ones you have not joined.",
            loadAvatar = viewModel::loadAvatar,
            onClick = { entry ->
                if (entry.joined) {
                    if (entry.isSpace) onOpenSpace(entry.roomId) else onOpenRoom(entry.roomId)
                } else {
                    viewModel.join(entry.roomId, entry.via) { isSpace ->
                        if (isSpace || entry.isSpace) onOpenSpace(entry.roomId) else onOpenRoom(entry.roomId)
                    }
                }
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SpaceList(
    entries: List<SpaceEntry>,
    loading: Boolean,
    emptyTitle: String,
    emptyBody: String,
    loadAvatar: suspend (String?) -> ByteArray?,
    onClick: (SpaceEntry) -> Unit,
    joiningId: String? = null,
    modifier: Modifier = Modifier,
) {
    if (loading && entries.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (entries.isEmpty()) {
        Column(
            modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.GridView,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
            )
            Text(
                emptyTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                emptyBody,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
    ) {
        items(entries, key = { it.roomId }) { entry ->
            SpaceRow(
                entry = entry,
                joining = joiningId == entry.roomId,
                loadAvatar = loadAvatar,
                onClick = { onClick(entry) },
            )
        }
    }
}

@Composable
private fun BrowseServerRow(host: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.TravelExplore, contentDescription = null, tint = scheme.onPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text("Browse this server", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (host.isBlank()) "Public rooms and spaces" else "Public rooms and spaces on $host",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
private fun SpaceRow(
    entry: SpaceEntry,
    joining: Boolean,
    loadAvatar: suspend (String?) -> ByteArray?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bitmap by produceState<ImageBitmap?>(null, entry.avatarMxc) {
        value = loadAvatar(entry.avatarMxc)?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    val initial = remember(entry.title) {
        entry.title.firstOrNull { !it.isWhitespace() }?.uppercaseChar()?.toString() ?: "#"
    }
    val subtitle = remember(entry) { spaceSubtitle(entry) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !joining, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(if (entry.isSpace) RoundedCornerShape(14.dp) else CircleShape)
                    .background(scheme.primary.copy(alpha = 0.85f)),
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
                    Text(initial, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (!entry.joined) {
                if (joining) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        "Join",
                        color = scheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            color = scheme.outlineVariant.copy(alpha = 0.45f),
        )
    }
}

private fun spaceSubtitle(entry: SpaceEntry): String {
    val parts = buildList {
        if (entry.isSpace) add("Space")
        if (entry.childCount > 0) add("${entry.childCount} inside")
        if (entry.memberCount > 0) add("${entry.memberCount} people")
        if (!entry.joined) add(entry.joinNote ?: "Not joined")
        entry.topic?.let { add(it) }
    }
    return parts.joinToString(" · ")
}

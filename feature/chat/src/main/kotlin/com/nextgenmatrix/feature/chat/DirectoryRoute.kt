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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextgenmatrix.core.model.DirectoryEntry
import kotlinx.coroutines.delay

@Composable
fun DirectoryRoute(
    onOpenRoom: (String) -> Unit,
    onOpenSpace: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DirectoryViewModel = hiltViewModel(),
) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        delay(350)
        viewModel.search(query)
    }
    val page by viewModel.page.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val joiningId by viewModel.joiningId.collectAsStateWithLifecycle()
    val joinError by viewModel.joinError.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text("Search public rooms") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            shape = RoundedCornerShape(18.dp),
        )
        Text(
            "Rooms and spaces published on this server. Private ones are not listed.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        joinError?.let { message ->
            Text(
                message,
                color = scheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (loading && page.entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        if (page.entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing published matches that search.",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(page.entries, key = { it.roomId }) { entry ->
                DirectoryRow(
                    entry = entry,
                    joining = joiningId == entry.roomId,
                    loadAvatar = viewModel::loadAvatar,
                    onClick = {
                        if (entry.joined) {
                            if (entry.isSpace) onOpenSpace(entry.roomId) else onOpenRoom(entry.roomId)
                        } else {
                            viewModel.join(entry) { isSpace ->
                                if (isSpace) onOpenSpace(entry.roomId) else onOpenRoom(entry.roomId)
                            }
                        }
                    },
                )
            }
            if (page.hasMore) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(onClick = viewModel::loadMore) { Text("Load more") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryRow(
    entry: DirectoryEntry,
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
    val subtitle = remember(entry) {
        buildList {
            if (entry.isSpace) add("Space")
            if (entry.memberCount > 0) add("${entry.memberCount} people")
            if (!entry.joined) add(entry.joinNote ?: "Not joined")
            entry.topic?.let { add(it) }
        }.joinToString(" · ")
    }
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
                    Image(bitmap!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
                    Text("Join", color = scheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 72.dp),
            color = scheme.outlineVariant.copy(alpha = 0.45f),
        )
    }
}

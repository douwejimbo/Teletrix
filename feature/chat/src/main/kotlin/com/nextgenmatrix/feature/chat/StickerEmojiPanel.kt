package com.nextgenmatrix.feature.chat

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition

sealed interface StickerPanelTab {
    data object Emoji : StickerPanelTab
    data object Recent : StickerPanelTab
    data class Pack(val id: String) : StickerPanelTab
}

@Composable
fun StickerEmojiPanel(
    tab: StickerPanelTab,
    onTab: (StickerPanelTab) -> Unit,
    recents: List<BuiltInSticker>,
    onSticker: (BuiltInSticker) -> Unit,
    onEmoji: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .height(348.dp)
            .background(scheme.surfaceContainer),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (tab) {
                StickerPanelTab.Emoji -> EmojiKeyboard(
                    onEmoji = onEmoji,
                    modifier = Modifier.fillMaxSize(),
                )
                StickerPanelTab.Recent -> {
                    if (recents.isEmpty()) {
                        Text(
                            "Stickers you send show up here",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        StickerGrid(recents, onSticker)
                    }
                }
                is StickerPanelTab.Pack -> {
                    val pack = StickerCatalog.packById(tab.id)
                    if (pack == null) {
                        StickerGrid(StickerCatalog.packs.first().stickers, onSticker)
                    } else {
                        StickerGrid(pack.stickers, onSticker)
                    }
                }
            }
        }
        if (tab !is StickerPanelTab.Emoji) {
            Text(
                "Noto Emoji Animation · Google · CC BY 4.0",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))
        PackTabRow(
            tab = tab,
            onTab = onTab,
            onBackspace = onBackspace,
        )
    }
}

@Composable
private fun EmojiKeyboard(
    onEmoji: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onEmojiState = rememberUpdatedState(onEmoji)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            EmojiPickerView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                emojiGridColumns = 8
                setOnEmojiPickedListener { item ->
                    onEmojiState.value(item.emoji)
                }
            }
        },
    )
}

@Composable
private fun StickerGrid(
    stickers: List<BuiltInSticker>,
    onSticker: (BuiltInSticker) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(stickers, key = { it.id }) { sticker ->
            StickerCell(sticker, onClick = { onSticker(sticker) })
        }
    }
}

@Composable
private fun StickerCell(
    sticker: BuiltInSticker,
    onClick: () -> Unit,
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(sticker.assetPath))
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LottieAnimation(
            composition = composition,
            iterations = LottieConstants.IterateForever,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
        )
    }
}

@Composable
private fun PackTabRow(
    tab: StickerPanelTab,
    onTab: (StickerPanelTab) -> Unit,
    onBackspace: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PackTabButton(
                selected = tab is StickerPanelTab.Emoji,
                onClick = { onTab(StickerPanelTab.Emoji) },
                label = "Emoji",
            ) {
                Icon(
                    Icons.Outlined.Mood,
                    contentDescription = null,
                    tint = if (tab is StickerPanelTab.Emoji) scheme.primary else scheme.onSurfaceVariant,
                )
            }
            PackTabButton(
                selected = tab is StickerPanelTab.Recent,
                onClick = { onTab(StickerPanelTab.Recent) },
                label = "Recent stickers",
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = if (tab is StickerPanelTab.Recent) scheme.primary else scheme.onSurfaceVariant,
                )
            }
            StickerCatalog.packs.forEach { pack ->
                val selected = tab is StickerPanelTab.Pack && tab.id == pack.id
                PackTabButton(
                    selected = selected,
                    onClick = { onTab(StickerPanelTab.Pack(pack.id)) },
                    label = pack.title,
                ) {
                    Text(pack.tabEmoji, fontSize = 22.sp)
                }
            }
        }
        if (tab is StickerPanelTab.Emoji) {
            IconButton(onClick = onBackspace) {
                Icon(
                    Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "Backspace",
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PackTabButton(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(42.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.secondaryContainer else scheme.surfaceContainer)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

package com.nextgenmatrix.feature.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nextgenmatrix.core.model.AccountSummary

@Composable
fun ProfileRoute(
    account: AccountSummary?,
    loadAvatar: suspend (String?) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    if (account == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Not signed in", color = scheme.onSurfaceVariant)
        }
        return
    }
    val name = account.displayName?.takeIf { it.isNotBlank() }
        ?: account.userId.substringAfter("@").substringBefore(":").ifBlank { account.userId }
    val bitmap by produceState<ImageBitmap?>(null, account.avatarMxc) {
        value = loadAvatar(account.avatarMxc)?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    val initial = remember(name) { name.firstOrNull { !it.isWhitespace() }?.uppercaseChar()?.toString() ?: "?" }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(scheme.primary),
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
                Text(initial, color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            account.userId,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = scheme.surface.copy(alpha = 0.82f),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                ProfileLine(
                    "Homeserver",
                    account.homeserverUrl
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .trimEnd('/'),
                )
                account.deviceId?.let { ProfileLine("This device", it) }
            }
        }
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

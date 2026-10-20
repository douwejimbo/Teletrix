package com.nextgenmatrix.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextgenmatrix.core.ui.theme.AppContrastMode
import com.nextgenmatrix.core.ui.theme.AppThemeMode

@Composable
fun SettingsRoute(
    themeMode: AppThemeMode,
    onThemeMode: (AppThemeMode) -> Unit,
    contrastMode: AppContrastMode,
    onContrastMode: (AppContrastMode) -> Unit,
    onVerifySession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var biometric by remember { mutableStateOf(false) }
    var secureScreen by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = scheme.surface.copy(alpha = 0.82f),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Privacy & security",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    RowSwitch("Biometric app lock", biometric) { biometric = it }
                    RowSwitch("Screenshot protection (FLAG_SECURE)", secureScreen) { secureScreen = it }
                    TextButton(onClick = onVerifySession, modifier = Modifier.fillMaxWidth()) {
                        Text("Verify this session")
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = scheme.surface.copy(alpha = 0.82f),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            AppThemeMode.Dark to "Dark",
                            AppThemeMode.Light to "Light",
                            AppThemeMode.System to "System",
                        ).forEach { (mode, label) ->
                            val selected = themeMode == mode
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (selected) scheme.primary else scheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.clickable { onThemeMode(mode) },
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                    RowSwitch("AMOLED black", contrastMode == AppContrastMode.Amoled) { enabled ->
                        onContrastMode(if (enabled) AppContrastMode.Amoled else AppContrastMode.Standard)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

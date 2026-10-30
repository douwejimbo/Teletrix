package com.nextgenmatrix.data.push

import android.content.Context
import com.nextgenmatrix.core.model.ChatNotifyMode
import com.nextgenmatrix.core.model.NotificationPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("teletrix_notify", Context.MODE_PRIVATE)

    private val _value = MutableStateFlow(read())
    val value: StateFlow<NotificationPrefs> = _value.asStateFlow()

    fun current(): NotificationPrefs = _value.value

    fun update(next: NotificationPrefs) {
        _value.value = next
        prefs.edit()
            .putBoolean(KEY_ENABLED, next.enabled)
            .putString(KEY_DM, next.privateChats.name)
            .putString(KEY_GROUPS, next.groups.name)
            .putBoolean(KEY_REACTIONS, next.reactions)
            .apply()
    }

    private fun read(): NotificationPrefs = NotificationPrefs(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        privateChats = mode(prefs.getString(KEY_DM, null), ChatNotifyMode.All),
        groups = mode(prefs.getString(KEY_GROUPS, null), ChatNotifyMode.Mentions),
        reactions = prefs.getBoolean(KEY_REACTIONS, true),
    )

    private fun mode(raw: String?, fallback: ChatNotifyMode): ChatNotifyMode =
        ChatNotifyMode.entries.firstOrNull { it.name == raw } ?: fallback

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_DM = "private"
        const val KEY_GROUPS = "groups"
        const val KEY_REACTIONS = "reactions"
    }
}

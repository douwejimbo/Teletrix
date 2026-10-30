package com.nextgenmatrix.app

import androidx.lifecycle.ViewModel
import com.nextgenmatrix.core.model.NotificationPrefs
import com.nextgenmatrix.data.push.NotificationPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val store: NotificationPreferences,
) : ViewModel() {
    val prefs: StateFlow<NotificationPrefs> = store.value

    fun update(next: NotificationPrefs) {
        store.update(next)
    }
}

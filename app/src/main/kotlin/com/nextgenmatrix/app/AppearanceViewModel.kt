package com.nextgenmatrix.app

import android.content.Context
import androidx.lifecycle.ViewModel
import com.nextgenmatrix.core.ui.theme.AppContrastMode
import com.nextgenmatrix.core.ui.theme.AppThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Persists the theme choice. Dark is the default until the user picks something else. */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    @ApplicationContext context: Context,
) : ViewModel() {
    private val prefs = context.getSharedPreferences("teletrix_appearance", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME, null)
            ?.let { name -> AppThemeMode.entries.firstOrNull { it.name == name } }
            ?: AppThemeMode.Dark,
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _contrastMode = MutableStateFlow(
        if (prefs.getBoolean(KEY_AMOLED, false)) AppContrastMode.Amoled else AppContrastMode.Standard,
    )
    val contrastMode: StateFlow<AppContrastMode> = _contrastMode.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setContrastMode(mode: AppContrastMode) {
        _contrastMode.value = mode
        prefs.edit().putBoolean(KEY_AMOLED, mode == AppContrastMode.Amoled).apply()
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_AMOLED = "amoled"
    }
}

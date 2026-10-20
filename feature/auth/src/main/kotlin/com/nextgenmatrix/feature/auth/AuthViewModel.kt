package com.nextgenmatrix.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextgenmatrix.matrix.MatrixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val matrixRepository: MatrixRepository,
) : ViewModel() {

    private val _homeserver = MutableStateFlow("matrix.org")
    val homeserver: StateFlow<String> = _homeserver.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    val isLoggedIn = matrixRepository.isLoggedIn

    init {
        viewModelScope.launch {
            if (!matrixRepository.hasPersistedSession()) return@launch
            _restoring.value = true
            _status.value = "Restoring your session…"
            matrixRepository.restoreSession()
                .onSuccess { _status.value = "Signed in" }
                .onFailure { _status.value = it.message }
            _restoring.value = false
        }
    }

    fun onHomeserverChange(value: String) {
        _homeserver.value = value
    }

    fun discover() {
        viewModelScope.launch {
            _status.value = "Discovering…"
            matrixRepository.setHomeserver(_homeserver.value)
                .onSuccess { _status.value = "Homeserver ready (password=${it.supportsPasswordLogin}, OIDC=${it.supportsOidc})" }
                .onFailure { _status.value = it.message }
        }
    }

    fun login(user: String, pass: String) {
        viewModelScope.launch {
            _status.value = "Signing in…"
            matrixRepository.login(user, pass)
                .onSuccess { _status.value = "Signed in" }
                .onFailure { _status.value = it.message }
        }
    }

    fun restore() {
        viewModelScope.launch {
            _status.value = "Restoring…"
            matrixRepository.restoreSession()
                .onSuccess { _status.value = "Session restored" }
                .onFailure { _status.value = it.message }
        }
    }
}

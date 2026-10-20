package com.nextgenmatrix.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextgenmatrix.core.model.SessionVerificationPrompt
import com.nextgenmatrix.matrix.MatrixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionVerificationViewModel @Inject constructor(
    private val matrixRepository: MatrixRepository,
) : ViewModel() {
    val prompt: StateFlow<SessionVerificationPrompt?> =
        matrixRepository.sessionVerificationPrompt

    fun approve() {
        viewModelScope.launch { matrixRepository.approveSessionVerification() }
    }

    fun decline() {
        viewModelScope.launch { matrixRepository.declineSessionVerification() }
    }

    fun cancel() {
        viewModelScope.launch { matrixRepository.cancelSessionVerification() }
    }

    fun dismiss() {
        viewModelScope.launch { matrixRepository.dismissSessionVerificationPrompt() }
    }

    fun continueFromPending() {
        viewModelScope.launch { matrixRepository.continueSessionVerificationFromPending() }
    }

    fun requestVerification() {
        viewModelScope.launch { matrixRepository.requestSessionVerification() }
    }
}

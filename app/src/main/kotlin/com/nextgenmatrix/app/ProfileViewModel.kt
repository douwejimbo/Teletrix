package com.nextgenmatrix.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextgenmatrix.matrix.MatrixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val matrixRepository: MatrixRepository,
) : ViewModel() {
    val account = matrixRepository.account

    init {
        viewModelScope.launch { matrixRepository.refreshAccount() }
    }

    suspend fun loadAvatar(mxc: String?): ByteArray? =
        matrixRepository.loadMatrixAvatarThumbnail(mxc, maxWidth = 320, maxHeight = 320)
}

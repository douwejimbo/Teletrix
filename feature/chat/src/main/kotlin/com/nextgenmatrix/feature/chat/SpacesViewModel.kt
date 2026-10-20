package com.nextgenmatrix.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextgenmatrix.core.model.DirectoryEntry
import com.nextgenmatrix.core.model.DirectoryPage
import com.nextgenmatrix.core.model.SpaceEntry
import com.nextgenmatrix.matrix.MatrixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SpacesViewModel @Inject constructor(
    private val matrixRepository: MatrixRepository,
) : ViewModel() {

    private val _entries = MutableStateFlow<List<SpaceEntry>>(emptyList())
    val entries: StateFlow<List<SpaceEntry>> = _entries.asStateFlow()

    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _joiningId = MutableStateFlow<String?>(null)
    val joiningId: StateFlow<String?> = _joiningId.asStateFlow()

    private val _joinError = MutableStateFlow<String?>(null)
    val joinError: StateFlow<String?> = _joinError.asStateFlow()

    val account = matrixRepository.account

    fun loadTopLevel() {
        viewModelScope.launch {
            _loading.value = _entries.value.isEmpty()
            _title.value = null
            _entries.value = matrixRepository.topLevelSpaces()
            _loading.value = false
        }
    }

    fun loadSpace(spaceId: String) {
        viewModelScope.launch {
            _loading.value = _entries.value.isEmpty()
            val contents = matrixRepository.spaceContents(spaceId)
            _title.value = contents.space?.title
            _entries.value = contents.children
            _loading.value = false
        }
    }

    suspend fun loadAvatar(mxc: String?): ByteArray? = matrixRepository.loadMatrixAvatarThumbnail(mxc)

    fun join(roomId: String, via: List<String>, onJoined: (isSpace: Boolean) -> Unit) {
        if (_joiningId.value != null) return
        viewModelScope.launch {
            _joiningId.value = roomId
            _joinError.value = null
            matrixRepository.joinListedRoom(roomId, via)
                .onSuccess { isSpace -> onJoined(isSpace) }
                .onFailure { _joinError.value = it.message?.ifBlank { null } ?: "Couldn't join that room" }
            _joiningId.value = null
        }
    }
}

@HiltViewModel
class DirectoryViewModel @Inject constructor(
    private val matrixRepository: MatrixRepository,
) : ViewModel() {

    private val _page = MutableStateFlow(DirectoryPage(emptyList(), false))
    val page: StateFlow<DirectoryPage> = _page.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _joiningId = MutableStateFlow<String?>(null)
    val joiningId: StateFlow<String?> = _joiningId.asStateFlow()

    private val _joinError = MutableStateFlow<String?>(null)
    val joinError: StateFlow<String?> = _joinError.asStateFlow()

    private var queryGeneration = 0

    fun search(text: String) {
        val generation = ++queryGeneration
        viewModelScope.launch {
            _loading.value = _page.value.entries.isEmpty()
            val page = matrixRepository.searchPublicRooms(text)
            if (generation != queryGeneration) return@launch
            _page.value = page
            _loading.value = false
        }
    }

    fun loadMore() {
        if (!_page.value.hasMore || _loading.value) return
        val generation = queryGeneration
        viewModelScope.launch {
            _loading.value = true
            val page = matrixRepository.morePublicRooms()
            if (generation != queryGeneration) return@launch
            _page.value = page
            _loading.value = false
        }
    }

    fun join(entry: DirectoryEntry, onJoined: (isSpace: Boolean) -> Unit) {
        if (_joiningId.value != null) return
        viewModelScope.launch {
            _joiningId.value = entry.roomId
            _joinError.value = null
            matrixRepository.joinListedRoom(entry.roomId, entry.via)
                .onSuccess { isSpace ->
                    _page.value = _page.value.copy(
                        entries = _page.value.entries.map {
                            if (it.roomId == entry.roomId) it.copy(joined = true, isSpace = isSpace) else it
                        },
                    )
                    onJoined(isSpace)
                }
                .onFailure { _joinError.value = it.message?.ifBlank { null } ?: "Couldn't join that room" }
            _joiningId.value = null
        }
    }

    suspend fun loadAvatar(mxc: String?): ByteArray? = matrixRepository.loadMatrixAvatarThumbnail(mxc)
}

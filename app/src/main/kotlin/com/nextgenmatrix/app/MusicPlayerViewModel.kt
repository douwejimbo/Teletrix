package com.nextgenmatrix.app

import androidx.lifecycle.ViewModel
import com.nextgenmatrix.data.media.ChatMusicPlayer
import com.nextgenmatrix.data.media.NowPlaying
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val player: ChatMusicPlayer,
) : ViewModel() {
    val nowPlaying: StateFlow<NowPlaying?> = player.nowPlaying

    fun toggle() = player.toggle()

    fun next() = player.next()

    fun previous() = player.previous()
}

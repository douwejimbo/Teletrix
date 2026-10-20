package com.nextgenmatrix.data.media

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

data class MusicTrack(
    val id: String,
    val title: String,
    val uri: Uri,
)

data class NowPlaying(
    val trackId: String,
    val title: String,
    val isPlaying: Boolean,
)

/** Background playback for audio files shared in chats. Voice notes stay inline. */
interface ChatMusicPlayer {
    val nowPlaying: StateFlow<NowPlaying?>

    fun play(tracks: List<MusicTrack>, startIndex: Int)

    /** Replace the queue without restarting [currentId] from the beginning. */
    fun replaceQueue(tracks: List<MusicTrack>, currentId: String)

    fun toggle()

    fun next()

    fun previous()
}

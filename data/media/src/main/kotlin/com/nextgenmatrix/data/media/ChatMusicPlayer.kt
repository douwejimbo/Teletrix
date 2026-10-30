package com.nextgenmatrix.data.media

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

data class MusicTrack(
    val id: String,
    val title: String,
    val uri: Uri,
    val subtitle: String? = null,
    val isVoice: Boolean = false,
)

data class NowPlaying(
    val trackId: String,
    val title: String,
    val isPlaying: Boolean,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val subtitle: String? = null,
    val isVoice: Boolean = false,
)

/** Background playback for chat audio and voice notes. Survives leaving the room. */
interface ChatMusicPlayer {
    val nowPlaying: StateFlow<NowPlaying?>

    fun play(tracks: List<MusicTrack>, startIndex: Int)

    /** Replace the queue without restarting [currentId] from the beginning. */
    fun replaceQueue(tracks: List<MusicTrack>, currentId: String)

    fun toggle()

    fun next()

    fun previous()

    fun seekTo(positionMs: Long)

    fun stop()
}

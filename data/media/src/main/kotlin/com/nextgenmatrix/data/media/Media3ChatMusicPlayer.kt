package com.nextgenmatrix.data.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Media3ChatMusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : ChatMusicPlayer {

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    override val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private var controller: MediaController? = null
    private var connecting: kotlinx.coroutines.CompletableDeferred<MediaController>? = null

    override fun play(tracks: List<MusicTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        mainScope.launch {
            val c = awaitController()
            val index = startIndex.coerceIn(0, tracks.lastIndex)
            c.setMediaItems(tracks.map(::toItem), index, 0L)
            c.prepare()
            c.play()
            publish(c)
        }
    }

    override fun replaceQueue(tracks: List<MusicTrack>, currentId: String) {
        if (tracks.isEmpty()) return
        mainScope.launch {
            val c = awaitController()
            val index = tracks.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            val position = if (c.currentMediaItem?.mediaId == currentId) c.currentPosition else 0L
            val wasPlaying = c.isPlaying || c.playWhenReady
            c.setMediaItems(tracks.map(::toItem), index, position)
            c.prepare()
            if (wasPlaying) c.play()
            publish(c)
        }
    }

    override fun toggle() {
        mainScope.launch {
            val c = controller ?: return@launch
            if (c.isPlaying) c.pause() else c.play()
            publish(c)
        }
    }

    override fun next() {
        mainScope.launch {
            val c = controller ?: return@launch
            if (c.hasNextMediaItem()) {
                c.seekToNextMediaItem()
                c.play()
            }
            publish(c)
        }
    }

    override fun previous() {
        mainScope.launch {
            val c = controller ?: return@launch
            if (c.hasPreviousMediaItem()) {
                c.seekToPreviousMediaItem()
                c.play()
            } else {
                c.seekTo(0)
                c.play()
            }
            publish(c)
        }
    }

    private fun toItem(track: MusicTrack): MediaItem =
        MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title).build())
            .build()

    private suspend fun awaitController(): MediaController {
        controller?.let { return it }
        connecting?.let { return it.await() }
        val deferred = kotlinx.coroutines.CompletableDeferred<MediaController>()
        connecting = deferred
        val token = SessionToken(context, ComponentName(context, NextGenPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                try {
                    val created = future.get()
                    attach(created)
                    controller = created
                    deferred.complete(created)
                } catch (t: Throwable) {
                    deferred.completeExceptionally(t)
                } finally {
                    if (connecting === deferred) connecting = null
                }
            },
            context.mainExecutor,
        )
        return deferred.await()
    }

    private fun attach(player: MediaController) {
        player.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publish(player)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    publish(player)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    publish(player)
                }
            },
        )
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        if (item == null || player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
            _nowPlaying.value = null
            return
        }
        _nowPlaying.value = NowPlaying(
            trackId = item.mediaId,
            title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "Audio" },
            isPlaying = player.isPlaying,
        )
    }
}

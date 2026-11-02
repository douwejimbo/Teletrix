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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
    private var metaById: Map<String, MusicTrack> = emptyMap()
    private var ticker: Job? = null

    override fun play(tracks: List<MusicTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return
        metaById = tracks.associateBy { it.id }
        mainScope.launch {
            val c = awaitController()
            val index = startIndex.coerceIn(0, tracks.lastIndex)
            c.setMediaItems(tracks.map(::toItem), index, 0L)
            c.prepare()
            c.play()
            publish(c)
            startTicker()
        }
    }

    override fun replaceQueue(tracks: List<MusicTrack>, currentId: String) {
        if (tracks.isEmpty()) return
        metaById = tracks.associateBy { it.id }
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

    override fun seekTo(positionMs: Long) {
        mainScope.launch {
            val c = controller ?: return@launch
            c.seekTo(positionMs.coerceAtLeast(0L))
            publish(c)
        }
    }

    override fun stop() {
        mainScope.launch {
            ticker?.cancel()
            ticker = null
            val c = controller ?: return@launch
            c.stop()
            c.clearMediaItems()
            _nowPlaying.value = null
        }
    }

    private fun toItem(track: MusicTrack): MediaItem =
        MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.subtitle)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
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
                    if (isPlaying) startTicker() else ticker?.cancel()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    publish(player)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    publish(player)
                    if (playbackState == Player.STATE_ENDED) {
                        ticker?.cancel()
                    }
                }
            },
        )
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = mainScope.launch {
            while (isActive) {
                controller?.let(::publish)
                delay(400)
            }
        }
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        if (item == null || player.playbackState == Player.STATE_IDLE) {
            _nowPlaying.value = null
            return
        }
        if (player.playbackState == Player.STATE_ENDED && !player.playWhenReady) {
            _nowPlaying.value = null
            return
        }
        val meta = metaById[item.mediaId]
        _nowPlaying.value = NowPlaying(
            trackId = item.mediaId,
            title = item.mediaMetadata.title?.toString()
                .orEmpty()
                .ifBlank { meta?.title ?: "Audio" },
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0 } ?: 0L,
            subtitle = meta?.subtitle ?: item.mediaMetadata.artist?.toString(),
            isVoice = meta?.isVoice == true,
        )
    }
}

package com.music.zenith.ui.replay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.music.zenith.data.Http
import com.music.zenith.data.innertube.StreamResolver
import com.music.zenith.data.model.Song
import com.music.zenith.data.stats.ReplaySummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Replay's backing track: a clip of each of the listener's top songs, one
 * after another, faded in and out the way a recap reel is scored.
 *
 * Each clip starts at its hook rather than at 0:00, plays for [SEGMENT_MS] and
 * fades out before the next one fades in. Holding the story, or the share
 * sheet covering it, ducks the sound to nothing and brings it back when it
 * lets go — the same moments the story's own clock stops.
 *
 * It has a player of its own, so the app's queue is untouched. Whatever the
 * main player was doing is paused while the Replay is up and resumed after the
 * last fade-out, so the two never play over each other.
 */
@Composable
fun ReplayBacksound(
    summary: ReplaySummary,
    /** True while the story is held or something covers it: fade to silence. */
    silenced: Boolean,
    /** The app's own player, paused for as long as the Replay plays its clips. */
    mainPlayer: Player?,
) {
    val tracks = remember(summary) {
        summary.songs.map { it.song }
            .filter { !it.localUri.isNullOrBlank() || it.videoId.isNotBlank() }
            .take(MAX_TRACKS)
    }
    if (tracks.isNotEmpty()) BacksoundPlayer(tracks, silenced, mainPlayer)
}

@Composable
private fun BacksoundPlayer(
    tracks: List<Song>,
    silenced: Boolean,
    mainPlayer: Player?,
) {
    val context = LocalContext.current.applicationContext
    val silencedNow by rememberUpdatedState(silenced)
    val main by rememberUpdatedState(mainPlayer)
    // Two independent gains multiplied together: [level] is each clip's own
    // fade in and out, [duck] is the hold/share silence over the top of it.
    val level = remember { Animatable(0f) }
    val duck = remember { Animatable(1f) }
    val player = remember {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(context, OkHttpDataSource.Factory(Http.client)),
                ),
            )
            // Focus is not taken: the main player is paused by hand below, and
            // a second focus request would only make the system fight over it.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            .build()
            .apply {
                volume = 0f
                playWhenReady = false
            }
    }

    DisposableEffect(player) {
        val resumeMain = main?.isPlaying == true
        if (resumeMain) main?.pause()
        onDispose {
            val last = player.volume
            val resume = if (resumeMain) main else null
            // Outlives the composition on purpose: leaving the Replay must
            // fade the sound out rather than cut it.
            CoroutineScope(Dispatchers.Main.immediate).launch {
                for (step in FADE_STEPS downTo 1) {
                    player.volume = last * (step - 1) / FADE_STEPS
                    delay((FADE_OUT_MS / FADE_STEPS).toLong())
                }
                player.release()
                resume?.play()
            }
        }
    }

    LaunchedEffect(player) {
        snapshotFlow { level.value * duck.value }
            .collect { player.volume = (it * MAX_VOLUME).coerceIn(0f, 1f) }
    }

    LaunchedEffect(silenced) {
        if (silenced) {
            duck.animateTo(0f, tween(FADE_OUT_MS, easing = LinearEasing))
            player.pause()
        } else {
            if (player.mediaItemCount > 0) player.play()
            duck.animateTo(1f, tween(FADE_IN_MS, easing = LinearEasing))
        }
    }

    LaunchedEffect(tracks) {
        var index = 0
        var failures = 0
        while (isActive) {
            val song = tracks[index % tracks.size]
            index++
            val played = playSegment(player, level, { silencedNow }, song)
            if (played) {
                failures = 0
            } else if (++failures >= tracks.size) {
                // Nothing here can be played: stay quiet rather than spin.
                return@LaunchedEffect
            }
        }
    }
}

/** One clip, faded in, held and faded out. False when the track could not be played. */
private suspend fun playSegment(
    player: ExoPlayer,
    level: Animatable<Float, *>,
    silenced: () -> Boolean,
    song: Song,
): Boolean {
    val url = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            try {
                song.localUri?.takeIf { it.isNotBlank() } ?: StreamResolver.resolve(song.videoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    } ?: return false

    val ready = CompletableDeferred<Boolean>()
    val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) ready.complete(true)
        }

        override fun onPlayerError(error: PlaybackException) {
            ready.complete(false)
        }
    }
    player.addListener(listener)
    try {
        level.snapTo(0f)
        player.playWhenReady = false
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        if (withTimeoutOrNull(READY_TIMEOUT_MS) { ready.await() } != true) return false

        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        val start = if (duration > 0L) {
            (duration * HOOK_FRACTION).toLong().coerceAtMost((duration - SEGMENT_MS).coerceAtLeast(0L))
        } else {
            0L
        }
        player.seekTo(start)
        player.playWhenReady = !silenced()
        level.animateTo(1f, tween(FADE_IN_MS, easing = LinearEasing))

        val stop = (start + SEGMENT_MS).let { if (duration > 0L) minOf(it, duration) else it }
        // Position stops advancing while held, so the clip's length is only
        // ever counted in audible time.
        while (
            player.playbackState != Player.STATE_ENDED &&
            player.playbackState != Player.STATE_IDLE &&
            player.currentPosition < stop - FADE_OUT_MS
        ) {
            delay(POLL_MS)
        }
        level.animateTo(0f, tween(FADE_OUT_MS, easing = LinearEasing))
        return true
    } finally {
        player.removeListener(listener)
    }
}

/** How many of the top songs take turns. */
private const val MAX_TRACKS = 3

/** How long each clip plays, counted in audible time. */
private const val SEGMENT_MS = 30_000L

/** Where in the track a clip starts, as a share of its length: about where a hook sits. */
private const val HOOK_FRACTION = 0.30f

private const val FADE_IN_MS = 1_800
private const val FADE_OUT_MS = 1_400
private const val FADE_STEPS = 28

/** Quiet enough to sit under the story's text rather than over it. */
private const val MAX_VOLUME = 0.85f

private const val POLL_MS = 200L
private const val RESOLVE_TIMEOUT_MS = 15_000L
private const val READY_TIMEOUT_MS = 12_000L
@file:OptIn(UnstableApi::class)

package com.music.zenith.ui.replay

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.music.zenith.data.Http
import com.music.zenith.data.innertube.StreamResolver
import com.music.zenith.data.model.Song
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/**
 * Short-lived disk store for the Replay's backsound clips.
 *
 * A clip used to be resolved and streamed live at the moment its turn came, so
 * whether the music was there depended on how fast that one fetch happened to
 * be: sometimes it started with the story, sometimes late, sometimes never.
 * Here each track is resolved and stored in full ahead of its turn, then played
 * from disk. Nothing in it is meant to last: a clip is removed as soon as the
 * Replay moves on to the next song, and whatever is left goes when the Replay
 * closes (or, after a crash, the next time the store is opened).
 */
object ReplayClipCache {

    private const val DIR_NAME = "replay_backsound"

    /** A track bigger than this is streamed as before rather than stored. */
    private const val MAX_CLIP_BYTES = 24L * 1024 * 1024

    private val lock = Any()
    private var cache: SimpleCache? = null
    private var session = 0

    private fun open(context: Context): SimpleCache = synchronized(lock) {
        cache ?: run {
            val app = context.applicationContext
            val dir = File(app.cacheDir, DIR_NAME)
            val database = StandaloneDatabaseProvider(app)
            // Whatever an earlier run left behind (killed mid-Replay) is dropped.
            runCatching { SimpleCache.delete(dir, database) }
            SimpleCache(dir, NoOpCacheEvictor(), database).also { cache = it }
        }
    }

    private fun upstream(context: Context): DataSource.Factory =
        DefaultDataSource.Factory(context, OkHttpDataSource.Factory(Http.client))

    /** Marks a new Replay as the owner of the store. */
    fun openSession(): Int = synchronized(lock) { ++session }

    /**
     * Empties the store when the Replay that owns it closes. If a newer Replay
     * has opened in the meantime it is left alone, and that one clears up.
     * Disk work: call off the main thread.
     */
    fun closeSession(id: Int) {
        val current = synchronized(lock) { if (id == session) cache else null } ?: return
        current.keys.toList().forEach { runCatching { current.removeResource(it) } }
    }

    /** Removes one song's stored copy. Disk work: call off the main thread. */
    fun evict(song: Song) {
        if (!song.localUri.isNullOrBlank() || song.videoId.isBlank()) return
        val current = synchronized(lock) { cache } ?: return
        runCatching { current.removeResource(song.videoId) }
    }

    /**
     * Reads what [prepare] stored. Never writes: a track that could not be
     * stored just streams, as it always did, and local files pass straight through.
     */
    fun playbackFactory(context: Context): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(open(context))
            .setUpstreamDataSourceFactory(upstream(context))
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Gets [song] ready to play: resolves it and stores it on disk, keyed by
     * its videoId. Returns the URI to hand to the player, or null when the track
     * cannot be played at all. If storing fails the URL is still returned, so
     * the clip falls back to streaming instead of being skipped.
     */
    suspend fun prepare(context: Context, song: Song): String? = withContext(Dispatchers.IO) {
        song.localUri?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        val videoId = song.videoId.takeIf { it.isNotBlank() } ?: return@withContext null
        val url = try {
            StreamResolver.resolve(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext null
        }
        val job = currentCoroutineContext().job
        val store = open(context)
        if (!isStored(store, videoId)) {
            if (!download(context, store, videoId, url) { job.isActive }) {
                runCatching { store.removeResource(videoId) }
            }
        }
        url
    }

    private fun isStored(store: SimpleCache, key: String): Boolean = runCatching {
        val length = ContentMetadata.getContentLength(store.getContentMetadata(key))
        length > 0L && store.isCached(key, 0L, length)
    }.getOrDefault(false)

    private fun download(
        context: Context,
        store: SimpleCache,
        key: String,
        url: String,
        active: () -> Boolean,
    ): Boolean {
        val source = CacheDataSource.Factory()
            .setCache(store)
            .setUpstreamDataSourceFactory(upstream(context))
            .createDataSource()
        val spec = DataSpec.Builder().setUri(url).setKey(key).build()
        var aborted = false
        lateinit var writer: CacheWriter
        writer = CacheWriter(
            source,
            spec,
            null,
            CacheWriter.ProgressListener { _, bytesCached, _ ->
                if (bytesCached > MAX_CLIP_BYTES || !active()) {
                    aborted = true
                    writer.cancel()
                }
            },
        )
        val finished = try {
            writer.cache()
            true
        } catch (e: Exception) {
            false
        }
        return finished && !aborted
    }
}
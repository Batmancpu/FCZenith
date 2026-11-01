package com.music.bitchord.ui.player

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import com.music.bitchord.playback.PlaybackPosition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.bitchord.data.canvas.CanvasArtwork
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.core.animate
import androidx.compose.runtime.rememberUpdatedState

/**
 * The looping clip some releases publish alongside their cover, for [song], or
 * null — a miss is the normal answer.
 */
@Composable
internal fun rememberCanvasArtwork(song: Song): CanvasArtwork? {
    val canvasEnabled by PlayerSettings.animatedCanvas.collectAsStateWithLifecycle()
    val canvasOverCellular by PlayerSettings.canvasOverCellular.collectAsStateWithLifecycle()
    val prioritizeSpotifyCanvas by PlayerSettings.prioritizeSpotifyCanvas.collectAsStateWithLifecycle()
    val meteredConnection by PlayerSettings.meteredConnection.collectAsStateWithLifecycle()
    // The switch turns the feature off outright; this is the narrower "not
    // over cellular" case — see [PlayerSettings.canvasOverCellular] for why a
    // clip's own loop makes that worth guarding separately from a still image.
    val canvasAllowedNow = canvasEnabled && (meteredConnection != true || canvasOverCellular)
    // Keyed on the song's identity rather than its videoId: a version switch
    // swaps the videoId for another cut of the *same* song, and a canvas is
    // searched for by title and artist — resetting here threw the clip out
    // mid-loop, blanked the player down to the still sleeve and restarted it,
    // which is the flicker. A different song keys differently and starts
    // clean; the same-titled-impostor case is guarded where the clip is
    // adopted, by [CanvasArtwork.matches].
    var canvas by remember(song.title, song.artist) { mutableStateOf<CanvasArtwork?>(null) }
    LaunchedEffect(song.videoId, song.albumName, canvasAllowedNow, prioritizeSpotifyCanvas) {
        if (!canvasAllowedNow) {
            canvas = null
            return@LaunchedEffect
        }
        // Anything already settled for this track paints immediately: a
        // reopened player, or a track coming round again in the queue.
        //
        // Across a version switch the clip found for the last cut is carried
        // over rather than dropped — same title and artist, so the same
        // search, so the same clip — and only while it still *answers* to
        // what is playing: [CanvasArtwork.matches] is what stops a preserved
        // clip from being a different song's that happens to share a title.
        canvas = PlayerPlatform.host.cachedCanvas(song)
            ?: canvas?.takeIf { it.matches(song.title, song.artist, song.albumName) }

        // The album name is looked up separately and lands a moment after the
        // player opens, and it is the field that makes the catalogue searches
        // match. Give it that moment: if it arrives, this effect restarts and
        // all that was spent waiting is the wait. If it never does — a track
        // with no album, or a lookup that failed — the search still goes out,
        // just a beat later, which is imperceptible for decoration.
        if (canvas == null && song.albumName == null) delay(ALBUM_SETTLE_MS)
        // Keep what an earlier pass found if this one comes back empty, rather
        // than pulling a playing clip out from under itself.
        canvas = PlayerPlatform.host.canvasFor(song) ?: canvas
    }

    return canvas
}

/**
 * The player's cover: the one request every surface draws it from, whether it
 * has loaded, and the bounded retries after a failure.
 *
 * Everything here is keyed on the cover's [url] — see [rememberPlayerArtwork].
 */
@Stable
internal class PlayerArtwork(val url: String?, private val context: PlatformContext) {
    /**
     * Whether the cover is on screen. Keyed on the artwork rather than on the
     * track, because that is what it actually describes and because only Coil
     * can set it back to true.
     */
    var loaded by mutableStateOf(false)
        private set

    /**
     * Which go at this cover we are on, and the reason there is more than one.
     *
     * Coil does not retry: a request that fails is over, and the state it leaves
     * behind is the state this screen keeps until the model changes — which,
     * keyed on the cover, means until the next track. One dropped connection at
     * the wrong moment and the player showed its placeholder tile for a song it
     * would have drawn perfectly a second later, with the widget and the
     * notification both showing the cover from cache the whole time.
     *
     * Bounded and spaced, because the usual reason a cover fails is that there
     * is no network at all, and a retry per recomposition — which is what an
     * unremembered request effectively gave — is a spin, not a recovery.
     */
    var attempt by mutableIntStateOf(0)
        private set

    /** Latched on an error, and only upwards — cleared by the retry itself. */
    internal var failed by mutableStateOf(false)

    /**
     * The one request for this cover, built once.
     *
     * Built once rather than inline at the sleeve, because Coil compares models
     * to decide whether to start a new load, and two separately built requests
     * are never equal — `ImageRequest` has no `equals`, and neither does the
     * size resolver `.size()` hands it. Inline, *every recomposition* was a new
     * load. The player recomposes at least twice a second off the position
     * tick, and each pass pushed the painter back through Loading before it
     * settled on Success again, which is exactly the [loaded] this screen hangs
     * the full-bleed banner, the sleeve's shadow and its placeholder icon on.
     *
     * Remembered on the cover and the attempt, so it changes when the picture
     * changes and when a retry is deliberately asked for, and at no other time.
     */
    val request: ImageRequest by derivedStateOf {
        val attempt = attempt
        ImageRequest.Builder(context)
            .data(url)
            .size(ART_PX)
            // Dissolve antar cover sudah ditangani ArtworkDissolve. Crossfade
            // 200ms bawaan ImageLoader membuat cover lama (layer outgoing) fade-in
            // dari transparan dan cover baru fade-in dari kosong, sementara
            // dissolve memudarkan cover lama: dua fade bertumpuk = alpha gabungan
            // turun sebentar, dan itulah "blink"-nya. Tidak memengaruhi memory
            // cache key, jadi tetap berbagi entry dengan mesh dan palette.
            .crossfade(false)
            // What makes a retry a new request as far as Coil's model comparison
            // is concerned. Only from the second go onwards, so the ordinary
            // request stays byte-identical to the one the mesh and the palette
            // make of the same cover and goes on sharing their memory-cache
            // entry. The disk key is unaffected either way.
            .apply { if (attempt > 0) memoryCacheKeyExtra("attempt", attempt.toString()) }
            .build()
    }

    /** Fed from each painter drawing [request]. */
    fun onState(state: AsyncImagePainter.State) {
        loaded = state is AsyncImagePainter.State.Success
        // Only the failure is latched, and only upwards: the retry that clears
        // it is [failed]'s own effect, and clearing it from a Loading state here
        // would cancel that effect's wait every time the painter passed back
        // through Loading.
        if (state is AsyncImagePainter.State.Success) failed = false
        if (state is AsyncImagePainter.State.Error) failed = true
    }

    internal fun retry() {
        failed = false
        attempt++
    }
}

@Composable
internal fun rememberPlayerArtwork(remoteArt: String?): PlayerArtwork {
    val context = LocalPlatformContext.current
    val artUrl = remoteArt?.artworkAt(ART_PX)
    val art = remember(artUrl) { PlayerArtwork(artUrl, context) }
    LaunchedEffect(artUrl, art.failed) {
        // A track with no artwork at all fails immediately and would fail
        // identically three more times: there is no request to make, so there is
        // nothing a second go could do differently.
        if (artUrl == null || !art.failed || art.attempt >= ART_RETRIES) return@LaunchedEffect
        delay(ART_RETRY_DELAY_MS)
        art.retry()
    }
    return art
}

/**
 * Dissolve dari cover yang tadi tampil ke cover penggantinya.
 *
 * Tanpa ini sleeve langsung berganti (cut) begitu model gambar berubah, jadi
 * mix yang sudah mengalir mulus antar lagu berakhir dengan gambar yang
 * "loncat". [outgoing] dipertahankan di atas cover baru lalu dipudarkan oleh
 * [progress] (0 = masih cover lama, 1 = tinggal cover baru).
 */
@Stable
internal class ArtworkDissolve(first: PlayerArtwork) {
    private var current: PlayerArtwork = first

    /**
     * Apakah lagu sebelumnya sedang menampilkan clip animasi saat komposisi
     * terakhir. Dibaca saat [retarget], sebelum komposisi baru menimpanya.
     */
    internal var lastClipShowing = false

    /** Indeks antrean pada komposisi terakhir. Dibaca saat [retarget]. */
    internal var lastQueueIndex = -1

    /**
     * Arah gerak transisi: +1 untuk next (cover lama keluar ke kiri, cover baru
     * masuk dari kanan), -1 untuk previous (kebalikannya).
     */
    var direction by mutableFloatStateOf(1f)
        private set

    /** Seberapa besar gerakan transisi: 1 untuk skip, kecil saat mix otomatis. */
    var punch by mutableFloatStateOf(1f)

    /** Cover yang sedang dipudarkan, atau null kalau tidak ada. */
    var outgoing by mutableStateOf<PlayerArtwork?>(null)
        private set

    /**
     * Cover yang dipegang layer "lama" yang SELALU ter-mount. Layer itu sudah
     * menampilkan cover ini sebelum lagu berganti, jadi saat transisi mulai tidak
     * ada AsyncImage baru yang harus dimuat (itulah penyebab blink cover lama).
     */
    var held by mutableStateOf(first)
        private set

    /** 0f saat cover lama masih utuh, 1f saat hanya cover baru yang tersisa. */
    var progress by mutableFloatStateOf(1f)

    /**
     * Frame terakhir clip canvas, diambil tepat saat next/prev ditekan. Selama
     * ada, layer cover lama menggambar frame ini (bukan thumbnail) dan
     * transisinya memakai blur-zoom dissolve, bukan sapuan geser.
     */
    var frame by mutableStateOf<ImageBitmap?>(null)
        private set

    /**
     * Frame terakhir Spotify Canvas layar penuh, diambil saat next/prev ditekan.
     * Ditahan tersembunyi sampai lagu benar-benar pindah, lalu menutup layar dan
     * memudar dengan blur + zoom (lihat [runFullTransition]).
     */
    var fullFrame by mutableStateOf<ImageBitmap?>(null)
        private set

    /** 0f = frame menutup layar, 1f = hilang (juga saat frame hanya ditahan menunggu lagu pindah). */
    var fullProgress by mutableFloatStateOf(1f)
        private set

    /**
     * Apakah dek kaca (blur bawah + dim) sedang terbuka saat [fullFrame] diambil.
     * Frame hanya berisi video, jadi tampilan dek itu digambar ulang di atasnya
     * supaya bagian bawah ikut memudar bersama frame, bukan langsung berubah.
     */
    var fullFrameDeck by mutableStateOf(false)
        private set

    /** Blur backdrop yang sedang tampil; diisi layar tiap komposisi, dibaca saat [retarget]. */
    var shownBlur: ImageBitmap? = null

    /** Blur backdrop dari cover yang sedang dipudarkan; ditahan selama [outgoing] ada. */
    var heldBlur by mutableStateOf<ImageBitmap?>(null)
        private set

    internal fun retarget(next: PlayerArtwork, enabled: Boolean, queueIndex: Int = -1) {
        val old = current
        if (old === next) return
        current = next
        // Indeks antrean mundur = previous. Selain itu (next, auto-advance)
        // dianggap maju.
        direction = if (queueIndex >= 0 && lastQueueIndex >= 0 && queueIndex < lastQueueIndex) -1f else 1f
        val framed = frame != null
        // Cover yang BENAR-BENAR sedang terlihat. Kalau transisi sebelumnya belum
        // sempat menyapu (cover lama masih menutup layar) lalu state lagu berkedip
        // (lagu 2 -> lagu 1 -> lagu 2), [old] belum pernah tampil. Memasangnya
        // sebagai lapisan lama itulah yang memunculkan cover 2 sebentar, lalu
        // kembali ke cover 1 sebelum disapu. Dibaca tanpa observasi supaya layar
        // tidak recompose di tiap frame animasi.
        val inFlight = androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { outgoing }
        val flightProgress = androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { progress }
        val visible = if (inFlight != null && flightProgress < RETARGET_KEEP_PROGRESS) inFlight else old
        if (enabled && next.url != null && (visible.url != null || framed)) {
            held = visible
            outgoing = visible
            progress = 0f
            if (visible !== inFlight) heldBlur = shownBlur
        } else {
            held = next
            outgoing = null
            progress = 1f
            frame = null
            heldBlur = null
        }
    }

    internal fun finish() {
        progress = 1f
        outgoing = null
        held = current
        frame = null
        heldBlur = null
    }

    /** Simpan frame clip yang tampil sebelum skip; null membersihkan sisa sebelumnya. */
    internal fun holdFrame(image: ImageBitmap?) {
        frame = image
    }

    /** Lepas frame yang tidak pernah dipakai (lagu tidak jadi pindah atau cover sama). */
    internal fun dropFrame() {
        if (outgoing == null) frame = null
    }

    /** Tahan frame canvas layar penuh; tersembunyi sampai [runFullTransition] jalan. */
    internal fun holdFullFrame(image: ImageBitmap?, deckUp: Boolean = false) {
        if (image == null) return
        fullFrame = image
        fullFrameDeck = deckUp
        fullProgress = 1f
    }

    /** Lepas frame layar penuh yang tidak pernah dipakai, kecuali transisinya sedang jalan. */
    internal fun dropFullFrame() {
        if (fullProgress >= 1f) fullFrame = null
    }

    /**
     * Dipanggil saat lagu berganti. Kalau ada frame yang ditahan, frame itu
     * langsung menutup layar lalu memudar menuju layar lagu baru. Bila transisi
     * sebelumnya terpotong di tengah, dilanjutkan dari titik itu, bukan diulang.
     */
    internal suspend fun runFullTransition(ready: suspend () -> Unit = {}) {
        if (fullFrame == null) return
        val from = if (fullProgress < 1f) fullProgress else 0f
        fullProgress = from
        // Frame lama sudah menutup layar; tunggu isi lagu baru siap baru memudar.
        if (from == 0f) ready()
        animate(from, 1f, animationSpec = tween(FRAME_DISSOLVE_MS, easing = ARTWORK_FLOW_EASING)) { value, _ ->
            fullProgress = value
        }
        fullFrame = null
    }
}

@Composable
internal fun rememberArtworkDissolve(
    art: PlayerArtwork,
    mixing: Boolean,
    clipShowing: Boolean = false,
    queueIndex: Int = -1,
    skipFadeMs: Long = 0L,
): ArtworkDissolve {
    val reduceAnimation by PlayerSettings.reduceAnimation.collectAsStateWithLifecycle()
    val dissolve = remember { ArtworkDissolve(art) }
    // Dilakukan saat komposisi, sebelum sleeve membacanya: frame pertama cover
    // baru harus sudah punya cover lama di atasnya, kalau tidak frame itu
    // adalah cut-nya.
    // Lagu lama yang clip-nya sedang berputar lalu diganti (next/prev): langsung
    // ke cover berikutnya, tanpa memunculkan thumbnail lama dulu.
    remember(art) {
        dissolve.punch = if (mixing) 0.35f else 1f
        // Spotify Canvas layar penuh sudah menahan frame terakhirnya sendiri
        // (fullFrame) dan memudarkannya ke layar lagu baru. Sleeve tidak boleh
        // ikut menyapu thumbnail lagu lama di bawahnya, kalau tidak thumbnail
        // lagu 1 muncul di lagu 2 alih-alih gambar terakhir canvas.
        dissolve.retarget(
            art,
            enabled = !reduceAnimation && dissolve.fullFrame == null &&
                (dissolve.frame != null || !dissolve.lastClipShowing),
            queueIndex = queueIndex,
        )
    }
    SideEffect {
        dissolve.lastClipShowing = clipShowing
        dissolve.lastQueueIndex = queueIndex
    }
    // Frame yang ditahan tapi transisinya tak pernah mulai dilepas setelah jeda.
    // Dibaca lewat snapshotFlow supaya layar tidak recompose karenanya.
    LaunchedEffect(dissolve) {
        snapshotFlow { dissolve.frame }.collectLatest { held ->
            if (held == null) return@collectLatest
            delay(FRAME_HOLD_TIMEOUT_MS)
            dissolve.dropFrame()
        }
    }
    LaunchedEffect(dissolve) {
        snapshotFlow { dissolve.fullFrame }.collectLatest { held ->
            if (held == null) return@collectLatest
            delay(FRAME_HOLD_TIMEOUT_MS)
            dissolve.dropFullFrame()
        }
    }
    val mixingNow by rememberUpdatedState(mixing)
    val skipFadeNow by rememberUpdatedState(skipFadeMs)
    LaunchedEffect(art) {
        val shown = dissolve.outgoing ?: return@LaunchedEffect
        // Cover baru harus sudah ada untuk dijadikan tujuan dissolve; cover
        // yang tidak kunjung datang ditinggalkan, bukan ditutupi yang lama.
        withTimeoutOrNull(ART_DISSOLVE_WAIT_MS) { snapshotFlow { art.loaded }.first { it } }
        // State lagu berkedip dan kembali ke cover yang masih menutup layar:
        // tidak ada yang perlu disapu, cukup lepas lapisan lama setelah termuat.
        if (shown.url == art.url && dissolve.frame == null) {
            dissolve.finish()
            return@LaunchedEffect
        }
        // Dua frame supaya bitmap baru sudah tergambar sebelum sapuan mulai,
        // bukan menyapu ke layar kosong lalu pop-in di tengah animasi.
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        val duration = when {
            // Crossfade next/prev: cover lama melebur ke cover baru selama
            // audio-nya memudar, bukan lebih cepat atau lebih lambat.
            mixingNow && skipFadeNow > 0L -> skipFadeNow.toInt().coerceIn(400, 6_000)
            mixingNow -> MIX_DISSOLVE_MS
            dissolve.frame != null -> FRAME_DISSOLVE_MS
            else -> SKIP_DISSOLVE_MS
        }
        animate(0f, 1f, animationSpec = tween(duration, easing = ARTWORK_FLOW_EASING)) { value, _ ->
            dissolve.progress = value
        }
        dissolve.finish()
    }
    return dissolve
}

private const val ART_DISSOLVE_WAIT_MS = 2_500L

/** Selama progres sapuan masih di bawah ini, cover lama dianggap masih penuh terlihat. */
private const val RETARGET_KEEP_PROGRESS = 0.3f
private const val MIX_DISSOLVE_MS = 1_800
private const val SKIP_DISSOLVE_MS = 950
private const val FRAME_DISSOLVE_MS = 1_100
private const val FRAME_HOLD_TIMEOUT_MS = 5_000L

/** Mirip easeOutQuint: meluncur cepat di awal, lalu mengendap sangat halus. */
private val ARTWORK_FLOW_EASING = CubicBezierEasing(0.32f, 0.08f, 0.14f, 1f)

/**
 * The seek bar's own state: a drag in progress, and where the handle was
 * dropped until the player's position catches up with it.
 */
@Stable
internal class PlayerScrub {
    var scrubbing by mutableStateOf(false)
        private set
    var value by mutableFloatStateOf(0f)
        private set

    /**
     * After releasing the scrubber the player needs to buffer before it
     * reports the new position. Kept showing where the user dropped it so the
     * handle doesn't snap back and then jump forward once loading finishes.
     */
    internal var pendingSeek by mutableStateOf<Float?>(null)

    /** Where the handle is drawn. */
    fun shown(positionMs: Long, durationMs: Long): Float {
        val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
        return when {
            scrubbing -> value
            pendingSeek != null -> pendingSeek!!
            else -> fraction.coerceIn(0f, 1f)
        }
    }

    fun drag(to: Float) {
        scrubbing = true
        value = to
    }

    fun release(seekTo: (Float) -> Unit) {
        pendingSeek = value
        seekTo(value)
        scrubbing = false
    }
}

@Composable
internal fun rememberPlayerScrub(trackId: String, position: PlaybackPosition, durationMs: Long): PlayerScrub {
    val scrub = remember { PlayerScrub() }
    var pendingSeek by scrub::pendingSeek

    // Released as soon as the player's own position agrees with where the handle
    // was dropped — and unconditionally a few seconds later whether it agrees or
    // not.
    //
    // The agreement test alone is not enough, because it is the only thing that
    // ever cleared the override: if the position never passes close to the
    // target — a clamped or rejected seek, a rendition swapped underneath, a
    // progress sample that steps straight over the window — nothing releases it
    // and the handle sits frozen at the drop point for the rest of the track.
    // Audio and lyrics follow the real position perfectly throughout, so the
    // failure looks like a stuck seek bar on a track that is playing fine.
    //
    // Tolerance is absolute rather than a share of the duration: two percent is
    // a quarter-second on a jingle and twelve seconds on a long mix, and it is
    // the wall-clock gap that decides whether the handle appears to jump.
    //
    // Watched from inside the effect rather than keyed on the position, so the
    // screen that owns this does not have to read the playhead to drive it.
    LaunchedEffect(durationMs, pendingSeek) {
        val target = pendingSeek ?: return@LaunchedEffect
        if (durationMs <= 0) return@LaunchedEffect
        snapshotFlow { position.positionMs }.first { positionMs ->
            abs(positionMs - (target * durationMs).toLong()) < SEEK_SETTLE_TOLERANCE_MS
        }
        pendingSeek = null
    }
    LaunchedEffect(pendingSeek) {
        if (pendingSeek == null) return@LaunchedEffect
        delay(SEEK_SETTLE_TIMEOUT_MS)
        pendingSeek = null
    }
    LaunchedEffect(trackId) { pendingSeek = null }

    return scrub
}

/**
 * The volume bar's state: the system output level, tweened to whatever the
 * system reports, and followed exactly under a finger.
 */
@Stable
internal class PlayerVolume(
    private val system: SystemVolume,
    private val scope: CoroutineScope,
) {
    /**
     * Animatable rather than plain state: a hardware volume step is a jump of
     * 1/15th of the bar, which reads as a stutter unless it's tweened.
     */
    val level = Animatable(system.level.value)
    var dragging by mutableStateOf(false)
        private set

    fun drag(to: Float) {
        dragging = true
        // Follow the finger exactly; only external changes tween.
        scope.launch { level.snapTo(to) }
        system.set(to)
    }

    fun release() {
        dragging = false
    }
}

@Composable
internal fun rememberPlayerVolume(): PlayerVolume {
    val system = PlayerPlatform.host.volume
    val scope = rememberCoroutineScope()
    val volume = remember(system) { PlayerVolume(system, scope) }
    // Hardware volume keys and the system panel change the level behind our
    // back; the host follows them, and so does the bar.
    val systemVolume by system.level.collectAsStateWithLifecycle()

    // Glide to the level the system reports, but never fight the finger — a
    // drag writes the level, which calls straight back through here.
    LaunchedEffect(systemVolume) {
        if (!volume.dragging) {
            volume.level.animateTo(systemVolume, tween(durationMillis = 220, easing = FastOutSlowInEasing))
        }
    }

    return volume
}


package com.music.zenith.ui.replay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Job
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import com.music.zenith.ui.icons.SfIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.music.zenith.R
import com.music.zenith.data.model.CARD_ART_PX
import com.music.zenith.data.model.HEADER_ART_PX
import com.music.zenith.data.model.ROW_ART_PX
import com.music.zenith.data.model.artworkAt
import com.music.zenith.data.stats.ReplaySummary
import com.music.zenith.ui.player.MeshGradientBackground
import com.music.zenith.ui.player.MeshPalette
import com.music.zenith.ui.player.rememberArtworkColors
import com.music.zenith.ui.theme.AccentRed
import kotlinx.coroutines.launch

/**
 * The Replay as a run of full-screen cards you tap through.
 *
 * ## Why a story rather than a scroll
 *
 * The page below this already has every number on it, ranked and scrollable, and
 * that is the right shape for looking something up. It is the wrong shape for
 * being *told* something: a scroll gives every fact the same weight and lets the
 * eye run past the one that matters. A story gives each fact the whole screen
 * and a few seconds of nobody else's attention.
 *
 * ## The layout
 *
 * Every card is the same three bands, which is what makes eight of them read as
 * one piece rather than eight designs: a sentence at the top with the number in
 * it set bold, the thing that sentence is about in the middle, and the share
 * button at the bottom. The sentence carries the meaning, so a card that has
 * lost its artwork to a slow connection still says something.
 *
 * ## The mechanics people already know
 *
 * Tap the right of the screen to move on, the left to go back, hold anywhere to
 * stop the clock, swipe to move by hand, back or the cross to leave. None of it
 * is explained on screen: it is the same everywhere, and explaining it would be
 * the first thing on a page whose whole job is to be effortless.
 *
 * The auto-advance stops at the last card rather than closing — closing on a
 * timer takes the share button away from someone reaching for it.
 */
@Composable
fun ReplayStories(
    summary: ReplaySummary,
    start: ReplayStoryPage,
    onClose: () -> Unit,
    /** Shares the card that was on screen when the button was pressed. */
    onShare: (ReplayStoryPage) -> Unit,
    /**
     * Holds the clock while something is up in front of the story.
     *
     * The share sheet is the case: it covers the lower half of the card, takes a
     * second to draw its picture, and behind it the story was still counting
     * down — so by the time anyone had chosen an app, the card they were sending
     * was two cards further on than the one on screen.
     */
    paused: Boolean = false,
    /** The app's player, paused while the Replay plays its own backsound. */
    mainPlayer: Player? = null,
) {
    // A category with nothing in it has no card: an empty page in the middle of
    // a story reads as the app having lost something.
    val pages = remember(summary) {
        ReplayStoryPage.ordered.filter { page ->
            when (page) {
                ReplayStoryPage.ALBUMS -> summary.albums.isNotEmpty()
                ReplayStoryPage.GENRES -> summary.genres.isNotEmpty()
                else -> true
            }
        }
    }
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(start).coerceAtLeast(0),
        pageCount = { pages.size },
    )
    val scope = rememberCoroutineScope()
    var held by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val current by remember { derivedStateOf { pagerState.settledPage } }
    // A finger dragging the pager and a transition in flight both stop the
    // clock, so the auto-advance can never fire into a card that is mid-slide.
    val dragging by pagerState.interactionSource.collectIsDraggedAsState()
    val moving by remember { derivedStateOf { pagerState.targetPage != pagerState.settledPage } }
    var nudge by remember { mutableStateOf(0) }
    var restart by remember { mutableStateOf(0) }
    val transition = remember { arrayOfNulls<Job>(1) }
    val seen = remember { intArrayOf(-1, -1) }
    val clockStopped = held || paused || dragging || moving

    // Scored like a recap reel: clips of the top songs, faded in and out, and
    // ducked to silence whenever the story's own clock is stopped.
    ReplayBacksound(
        summary = summary,
        silenced = held || paused,
        mainPlayer = mainPlayer,
    )

    /**
     * Moves the story to [target] with one transition at a time.
     *
     * The previous transition is cancelled before the next starts, so two
     * scroll requests never fight and the pager can't be left between cards.
     * It runs on the composition's scope, so a page change can't cancel it.
     * [nudge] marks that someone else moved the story, and [restart] restarts
     * the clock when the request lands on the page already showing (a back tap
     * on the first card used to leave the clock dead).
     */
    fun goTo(target: Int, animate: Boolean) {
        val next = target.coerceIn(0, pages.lastIndex)
        nudge++
        if (next == pagerState.settledPage) restart++
        transition[0]?.cancel()
        transition[0] = scope.launch {
            if (animate) {
                pagerState.animateScrollToPage(
                    page = next,
                    animationSpec = tween(STEP_MILLIS, easing = FastOutSlowInEasing),
                )
            } else {
                pagerState.scrollToPage(next)
            }
        }
    }

    fun step(forward: Boolean) {
        val from = pagerState.targetPage
        if (forward && from == pages.lastIndex) {
            onClose()
            return
        }
        goTo(from + if (forward) 1 else -1, animate = true)
    }

    LaunchedEffect(current, clockStopped, restart) {
        if (seen[0] != current || seen[1] != restart) {
            seen[0] = current
            seen[1] = restart
            progress.snapTo(0f)
        }
        if (clockStopped) return@LaunchedEffect
        val ticket = nudge
        // Resumed from where a hold left it rather than restarted.
        val remaining = ((1f - progress.value) * PAGE_MILLIS).toInt().coerceAtLeast(0)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        // Someone moved the story while the clock was finishing: leave it be.
        if (ticket != nudge) return@LaunchedEffect
        if (current == pages.lastIndex) onClose() else goTo(current + 1, animate = true)
    }

    // The system bars are kept outside the frame rather than padded for inside
    // it: the card is a fixed canvas, so fitting the whole card into the safe
    // area leaves it identical everywhere and puts the letterboxing at the bars.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        StoryFrame {
            Stage(
                pages = pages,
                pagerState = pagerState,
                summary = summary,
                current = current,
                progress = progress,
                onHold = { held = it },
                onStep = ::step,
                onClose = onClose,
                onShare = onShare,
            )
        }
    }
}

/**
 * Holds a story to 9:16 whatever shape the screen is.
 *
 * A story is a fixed canvas, not a responsive layout: the type sizes, the
 * collage offsets and the room the headline is allowed are all set against one
 * set of proportions, and a 20:9 phone stretches that into a column with a hole
 * in the middle while a tablet flattens it. It is also the shape the shared
 * image comes out as, so a card the user sends looks like the card they were
 * looking at when they tapped share.
 *
 * Fitted rather than filled — whichever of width and height runs out first is
 * the one that sets the size, and the rest is black. Letterboxing is the honest
 * failure here: cropping would take the headline or the share button off the
 * edge of the screen on the exact devices most likely to be running this.
 */
@Composable
private fun StoryFrame(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fitsByWidth = maxWidth / maxHeight < STORY_ASPECT
        Box(
            Modifier
                .then(if (fitsByWidth) Modifier.fillMaxWidth() else Modifier.fillMaxHeight())
                .aspectRatio(STORY_ASPECT),
        ) {
            content()
        }
    }
}

/** The story card itself, inside whatever box [StoryFrame] gave it. */
@Composable
private fun Stage(
    pages: List<ReplayStoryPage>,
    pagerState: PagerState,
    summary: ReplaySummary,
    current: Int,
    progress: Animatable<Float, *>,
    onHold: (Boolean) -> Unit,
    onStep: (Boolean) -> Unit,
    onClose: () -> Unit,
    onShare: (ReplayStoryPage) -> Unit,
) {
    // The chrome follows the card that is mostly on screen, so the counter and
    // segments flip with the slide instead of a beat after it.
    val shown = pagerState.currentPage
    val hold by rememberUpdatedState(onHold)
    val step by rememberUpdatedState(onStep)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        hold(true)
                        tryAwaitRelease()
                        hold(false)
                    },
                    // Supplied, and deliberately empty: without it every
                    // press-and-release is reported as a tap and holding to
                    // pause would turn the page the moment the finger lifts.
                    onLongPress = {},
                    onTap = { offset -> step(offset.x >= size.width * BACK_ZONE) },
                )
            },
    ) {
        // Every card carries its own backdrop, so the colour slides with the
        // card instead of snapping when the story settles on the next one.
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            StoryPage(
                page = pages[index],
                summary = summary,
                onShare = onShare,
                pageOffset = { (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction },
            )
        }

        StoryChrome(
            label = summary.localizedLabel(LocalContext.current),
            count = pages.size,
            current = current,
            shown = shown,
            progress = { progress.value },
            onClose = onClose,
        )
    }
}

/**
 * The furniture that doesn't move between cards: the counter, the cross, the
 * run of progress segments and the title. The progress is read while drawing,
 * so the clock never recomposes the cards.
 */
@Composable
private fun StoryChrome(
    label: String,
    count: Int,
    current: Int,
    shown: Int,
    progress: () -> Float,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "%02d".format(shown + 1) + "  /  " + "%02d".format(count),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close_replay),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count) { index ->
                Segment(
                    fraction = {
                        when {
                            index < shown -> 1f
                            index > shown -> 0f
                            shown == current -> progress()
                            else -> 0f
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (label.length == 4 && label.all { it.isDigit() }) {
                    "Replay ${label.takeLast(2)}"
                } else {
                    "Replay · $label"
                },
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.W700,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(width = 26.dp, height = 17.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "Zenith",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun Segment(fraction: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(2.5.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.22f))
            .drawBehind {
                drawRect(
                    color = Color.White,
                    size = Size(this.size.width * fraction().coerceIn(0f, 1f), this.size.height),
                )
            },
    )
}

// ── One card ────────────────────────────────────────────────────────────────

/**
 * Fades and lifts whatever it is applied to once [reveal] reaches [start], over
 * [span] of the reveal. Read while drawing, so staging a card costs no
 * recomposition.
 */
private fun Modifier.staged(
    reveal: Animatable<Float, *>,
    start: Float,
    span: Float = 0.5f,
    rise: Dp = 26.dp,
): Modifier = graphicsLayer {
    val t = ((reveal.value - start) / span).coerceIn(0f, 1f)
    val e = 1f - (1f - t) * (1f - t) * (1f - t)
    alpha = e
    translationY = (1f - e) * rise.toPx()
}

@Composable
private fun StoryPage(
    page: ReplayStoryPage,
    summary: ReplaySummary,
    onShare: (ReplayStoryPage) -> Unit,
    pageOffset: () -> Float,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(1600, delayMillis = 100, easing = FastOutSlowInEasing))
    }
    Box(Modifier.fillMaxSize()) {
        StoryBackdrop(page, summary)
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // The content leads the backdrop: it leaves faster and
                    // arrives later, fading through the gap between cards.
                    val o = pageOffset().coerceIn(-1f, 1f)
                    val a = abs(o)
                    translationX = -o * this.size.width * 0.22f
                    alpha = (1f - a * 1.1f).coerceIn(0f, 1f)
                    scaleX = 1f - a * 0.05f
                    scaleY = 1f - a * 0.05f
                }
                .padding(horizontal = 20.dp)
                .padding(top = CHROME_HEIGHT, bottom = 18.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize()) {
                    val headline = summary.storyHeadline(LocalContext.current, page)
                    when (page) {
                        ReplayStoryPage.INTRO -> IntroCard(summary, headline, reveal)
                        ReplayStoryPage.MINUTES -> MinutesCard(summary, headline, reveal)
                        ReplayStoryPage.SONGS ->
                            SongsCard(headline, summary.songRows(STORY_ROWS), reveal)
                        ReplayStoryPage.ARTISTS ->
                            ArtistsCard(headline, summary.artistRows(STORY_ROWS), reveal)
                        ReplayStoryPage.ALBUMS ->
                            AlbumsCard(headline, summary.albumRows(STORY_ROWS), reveal)
                        ReplayStoryPage.GENRES -> GenresCard(summary, headline, reveal)
                        ReplayStoryPage.HABITS -> HabitsCard(summary, headline, reveal)
                        ReplayStoryPage.SUMMARY -> RecapCard(summary, headline, reveal)
                    }
                }
            }
            // The share button is on every card: the card somebody wants to
            // send is whichever one surprised them. It sends *this* card.
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.26f), CircleShape)
                        .clickable { onShare(page) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SfIcons.Share,
                        contentDescription = stringResource(R.string.share_my_replay),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * One card's own backdrop: the mesh coloured from what the card is about, then
 * its own atmosphere ([drawDecor]), then a vignette and film grain on all of
 * them so the eight read as one reel.
 */
@Composable
private fun StoryBackdrop(page: ReplayStoryPage, summary: ReplaySummary) {
    val artwork = summary.storyArtwork(page)
    val base = rememberArtworkColors(artwork)
    val palette = remember(base, page) { base.rotated(storyHue(page)) }
    Box(Modifier.fillMaxSize()) {
        MeshGradientBackground(
            palette = palette,
            modifier = Modifier.fillMaxSize(),
            trackKey = page.name,
            animated = true,
            colorFadeMillis = 900,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.45f),
                        0.35f to Color.Black.copy(alpha = 0.10f),
                        1.0f to Color.Black.copy(alpha = 0.34f),
                    ),
                ),
        )
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawDecor(page)
                drawVignetteAndGrain()
            },
        )
    }
}

/**
 * The sentence at the top of every card. Two weights in one line so the number
 * is the loud part of an ordinary sentence. [family] lets each card set it in
 * its own voice.
 */
@Composable
private fun Headline(
    parts: List<HeadlineRun>,
    modifier: Modifier = Modifier,
    family: FontFamily = FontFamily.Default,
    textSize: Int = 30,
) {
    Text(
        text = buildAnnotatedString {
            parts.forEach { run ->
                withStyle(
                    SpanStyle(
                        fontWeight = if (run.bold) FontWeight.W800 else FontWeight.W600,
                        color = if (run.bold) Color.White else Color.White.copy(alpha = 0.62f),
                    ),
                ) { append(run.text) }
            }
        },
        style = MaterialTheme.typography.displayLarge,
        fontFamily = family,
        fontSize = textSize.sp,
        lineHeight = (textSize * 1.22f).sp,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    edge: Color = Color.White.copy(alpha = 0.18f),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.06f)),
                ),
            )
            .border(0.8.dp, edge, shape),
    ) { content() }
}

@Composable
private fun GoldRule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.8.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, Champagne.copy(alpha = 0.55f), Color.Transparent),
                ),
            ),
    )
}

// ── The cards ───────────────────────────────────────────────────────────────

/** Intro — a pile of polaroids under a film-leader light leak. */
@Composable
private fun ColumnScope.IntroCard(
    summary: ReplaySummary,
    headline: List<HeadlineRun>,
    reveal: Animatable<Float, *>,
) {
    Headline(headline, Modifier.staged(reveal, 0f), family = FontFamily.Serif)
    Box(Modifier.weight(1f).fillMaxWidth()) {
        ArtworkCollage(summary, reveal, Modifier.fillMaxSize())
    }
    Text(
        text = stringResource(R.string.replay_private_counting),
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        textAlign = TextAlign.Center,
        color = Color.White.copy(alpha = 0.62f),
        modifier = Modifier.fillMaxWidth().staged(reveal, 0.55f),
    )
}

/** Minutes — a spinning record inside a clock dial. */
@Composable
private fun ColumnScope.MinutesCard(
    summary: ReplaySummary,
    headline: List<HeadlineRun>,
    reveal: Animatable<Float, *>,
) {
    val context = LocalContext.current
    Headline(headline, Modifier.staged(reveal, 0f))
    BoxWithConstraints(
        Modifier.weight(1f).fillMaxWidth().staged(reveal, 0.10f, 0.6f, 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        val d = minOf(maxWidth - 24.dp, maxHeight)
        HourRing(d, summary.peakHour)
        Vinyl(summary.songs.firstOrNull()?.song?.thumbnailUrl, d * 0.70f)
    }
    GlassCard(Modifier.fillMaxWidth().staged(reveal, 0.45f)) {
        Text(
            // "That's 0 hours" reads as the page failing to count rather than
            // as a small number. Under an hour, the plays are the only figure
            // worth restating.
            text = buildString {
                if (summary.hours >= 1) {
                    append(context.getString(R.string.replay_hours_across, grouped(summary.hours)))
                } else {
                    append(context.getString(R.string.replay_across))
                    append(" ")
                }
                if (summary.hours >= 1) {
                    append(", ")
                }
                append(context.resources.getQuantityString(
                    R.plurals.replay_play_count,
                    summary.totalPlays,
                    grouped(summary.totalPlays.toLong()),
                ))
                if (summary.peakHour != null) {
                    append(", mostly around ")
                    append(formatHour(context, summary.peakHour!!))
                    append(".")
                } else {
                    append(".")
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Serif,
            color = Color.White.copy(alpha = 0.82f),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        )
    }
}

/** Songs — a spotlit number one over a typed-out tracklist. */
@Composable
private fun ColumnScope.SongsCard(
    headline: List<HeadlineRun>,
    rows: List<ReplayRow>,
    reveal: Animatable<Float, *>,
) {
    val context = LocalContext.current
    val lead = rows.firstOrNull() ?: return
    val frame = RoundedCornerShape(8.dp)
    Headline(headline, Modifier.staged(reveal, 0f))
    Spacer(Modifier.height(22.dp))
    Row(
        Modifier.fillMaxWidth().staged(reveal, 0.12f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.drawBehind {
                val glow = this.size.maxDimension * 0.95f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Champagne.copy(alpha = 0.40f), Color.Transparent),
                        center = center,
                        radius = glow,
                    ),
                    radius = glow,
                )
            },
        ) {
            Cover(
                lead.artworkUrl, lead.title, 132.dp, frame, HEADER_ART_PX,
                modifier = Modifier.border(1.dp, Champagne.copy(alpha = 0.55f), frame),
                elevated = true,
            )
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NO. 1",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp,
                    color = Champagne,
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(0.8.dp)
                        .background(Champagne.copy(alpha = 0.45f)),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = lead.title,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Serif,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.W800,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            lead.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            GlassCard(corner = 50.dp) {
                Text(
                    text = "${formatListening(context, lead.ms)} · " + context.resources.getQuantityString(
                        R.plurals.replay_play_count,
                        lead.plays,
                        grouped(lead.plays.toLong()),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }
    Spacer(Modifier.weight(1f))
    Column(Modifier.fillMaxWidth().staged(reveal, 0.35f, 0.55f)) {
        rows.drop(1).forEachIndexed { index, row ->
            if (index > 0) GoldRule()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "%02d".format(row.rank),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 28.sp,
                    color = Champagne,
                    modifier = Modifier.width(44.dp),
                )
                Cover(row.artworkUrl, row.title, 44.dp, RoundedCornerShape(6.dp), ROW_ART_PX)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.W600,
                        color = Color.White.copy(alpha = 0.95f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatListening(context, row.ms),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

/** Artists — a headliner under stage beams, ringed by pulsing halos. */
@Composable
private fun ColumnScope.ArtistsCard(
    headline: List<HeadlineRun>,
    rows: List<ReplayRow>,
    reveal: Animatable<Float, *>,
) {
    val context = LocalContext.current
    val lead = rows.firstOrNull() ?: return
    Headline(headline, Modifier.staged(reveal, 0f))
    Spacer(Modifier.height(8.dp))
    BoxWithConstraints(
        Modifier.weight(1f).fillMaxWidth().staged(reveal, 0.10f, 0.6f, 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        val d = minOf(maxWidth * 0.55f, maxHeight)
        HaloAvatar(lead.artworkUrl, lead.title, d)
    }
    Text(
        text = lead.title,
        style = MaterialTheme.typography.headlineMedium,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.W800,
        textAlign = TextAlign.Center,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().staged(reveal, 0.35f),
    )
    Text(
        text = "${formatListening(context, lead.ms)} · " + context.resources.getQuantityString(
            R.plurals.replay_play_count,
            lead.plays,
            grouped(lead.plays.toLong()),
        ),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = Color.White.copy(alpha = 0.58f),
        modifier = Modifier.fillMaxWidth().staged(reveal, 0.40f),
    )
    Spacer(Modifier.height(14.dp))
    Row(
        Modifier.fillMaxWidth().staged(reveal, 0.50f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.drop(1).forEach { row ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%02d".format(row.rank),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Champagne.copy(alpha = 0.9f),
                )
                Spacer(Modifier.height(4.dp))
                Cover(row.artworkUrl, row.title, 60.dp, CircleShape, ROW_ART_PX)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.W600,
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatListening(context, row.ms),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }
}

/** Albums — a sleeve with its record sliding out, and the runners-up as a shelf. */
@Composable
private fun ColumnScope.AlbumsCard(
    headline: List<HeadlineRun>,
    rows: List<ReplayRow>,
    reveal: Animatable<Float, *>,
) {
    val context = LocalContext.current
    val lead = rows.firstOrNull() ?: return
    Headline(headline, Modifier.staged(reveal, 0f), family = FontFamily.Serif)
    BoxWithConstraints(
        Modifier.weight(1f).fillMaxWidth().staged(reveal, 0.10f, 0.6f, 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        val sleeve = minOf(maxWidth * 0.56f, maxHeight)
        Box(Modifier.width(sleeve * 1.42f).height(sleeve)) {
            Vinyl(
                url = lead.artworkUrl,
                diameter = sleeve * 0.98f,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
            Cover(lead.artworkUrl, lead.title, sleeve, RoundedCornerShape(4.dp), HEADER_ART_PX, elevated = true)
        }
    }
    Text(
        text = lead.title,
        style = MaterialTheme.typography.headlineMedium,
        fontFamily = FontFamily.Serif,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.W800,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.staged(reveal, 0.35f),
    )
    lead.subtitle?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.staged(reveal, 0.38f),
        )
    }
    Text(
        text = "${formatListening(context, lead.ms)} · " + context.resources.getQuantityString(
            R.plurals.replay_play_count,
            lead.plays,
            grouped(lead.plays.toLong()),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.55f),
        modifier = Modifier.staged(reveal, 0.40f),
    )
    Spacer(Modifier.height(14.dp))
    rows.drop(1).chunked(2).forEachIndexed { line, pair ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .staged(reveal, 0.50f + line * 0.10f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            pair.forEach { row ->
                GlassCard(Modifier.weight(1f), corner = 16.dp) {
                    Row(
                        Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(row.artworkUrl, row.title, 48.dp, RoundedCornerShape(6.dp), ROW_ART_PX)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = row.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.W600,
                                color = Color.White.copy(alpha = 0.94f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = formatListening(context, row.ms),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Genres — the biggest word on the reel, over a live equalizer. */
@Composable
private fun ColumnScope.GenresCard(
    summary: ReplaySummary,
    headline: List<HeadlineRun>,
    reveal: Animatable<Float, *>,
) {
    val context = LocalContext.current
    val rows = summary.genreRows(STORY_ROWS)
    val lead = rows.firstOrNull() ?: return
    Headline(headline, Modifier.staged(reveal, 0f))
    Spacer(Modifier.height(14.dp))
    Text(
        text = lead.title,
        style = MaterialTheme.typography.displayLarge,
        fontSize = 56.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1).sp,
        fontWeight = FontWeight.W800,
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.staged(reveal, 0.10f),
    )
    Text(
        text = formatListening(context, lead.ms),
        style = MaterialTheme.typography.titleMedium,
        color = Champagne.copy(alpha = 0.9f),
        modifier = Modifier.staged(reveal, 0.16f),
    )
    Spacer(Modifier.weight(0.5f))
    EqualizerChart(rows, reveal, Modifier.weight(2.2f).fillMaxWidth())
    Spacer(Modifier.height(8.dp))
}

/** Habits — the numbers as ticket stubs pinned onto ruled diary paper. */
@Composable
private fun ColumnScope.HabitsCard(
    summary: ReplaySummary,
    headline: List<HeadlineRun>,
    reveal: Animatable<Float, *>,
) {
    val context = LocalContext.current
    Headline(headline, Modifier.staged(reveal, 0f), family = FontFamily.Serif)
    Spacer(Modifier.weight(1f))
    val tickets = buildList<Pair<String, String>> {
        if (summary.distinctAlbums > 0) {
            add(grouped(summary.distinctAlbums.toLong()) to stringResource(R.string.different_albums))
        }
        summary.busiestDay?.let {
            add(
                formatDay(context, it) to
                    stringResource(R.string.your_biggest_day, formatListening(context, summary.busiestDayMs)),
            )
        }
        summary.peakHour?.let {
            add(formatHour(context, it) to stringResource(R.string.when_you_listen_the_most))
        }
    }
    tickets.forEachIndexed { index, (value, label) ->
        Ticket(
            value = value,
            label = label,
            number = index + 1,
            angle = if (index % 2 == 0) -1.6f else 1.3f,
            modifier = Modifier.staged(reveal, 0.12f + index * 0.14f, 0.5f, 34.dp),
        )
    }
    Spacer(Modifier.weight(0.4f))
}

/** Summary — the finale: liner notes in a champagne frame. */
@Composable
private fun ColumnScope.RecapCard(
    summary: ReplaySummary,
    headline: List<HeadlineRun>,
    reveal: Animatable<Float, *>,
) {
    Headline(headline, Modifier.staged(reveal, 0f), family = FontFamily.Serif)
    Spacer(Modifier.height(18.dp))
    GlassCard(
        Modifier.fillMaxWidth().weight(1f).staged(reveal, 0.14f, 0.55f),
        corner = 26.dp,
        edge = Champagne.copy(alpha = 0.5f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "LINER NOTES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 3.sp,
                    color = Champagne,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatMinutes(summary.totalMs),
                    style = MaterialTheme.typography.displayLarge,
                    fontFamily = FontFamily.Serif,
                    fontSize = 64.sp,
                    lineHeight = 68.sp,
                    fontWeight = FontWeight.W800,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.minutes).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 2.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
            GoldRule()
            LinerItem(stringResource(R.string.top_song), summary.songs.firstOrNull()?.song?.title)
            LinerItem(stringResource(R.string.top_artist), summary.artists.firstOrNull()?.title)
            LinerItem(stringResource(R.string.top_album), summary.albums.firstOrNull()?.title)
            LinerItem(stringResource(R.string.top_genre), summary.genres.firstOrNull()?.title)
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.tap_share_for_picture),
        style = MaterialTheme.typography.bodyLarge,
        fontFamily = FontFamily.Serif,
        fontStyle = FontStyle.Italic,
        textAlign = TextAlign.Center,
        color = Color.White.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().staged(reveal, 0.60f),
    )
}

@Composable
private fun LinerItem(label: String, value: String?) {
    if (value == null) return
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.W600,
            letterSpacing = 2.5.sp,
            color = Champagne.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.W700,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Card pieces ─────────────────────────────────────────────────────────────

@Composable
private fun Polaroid(
    url: String,
    cover: Dp,
    angle: Float,
    modifier: Modifier = Modifier,
    tape: Boolean = false,
) {
    val pad = cover * 0.07f
    val frame = RoundedCornerShape(3.dp)
    Box(
        modifier
            .rotate(angle)
            .shadow(14.dp, frame, clip = false)
            .background(Paper, frame)
            .padding(start = pad, top = pad, end = pad, bottom = pad * 3.2f),
    ) {
        Cover(url, "", cover, RoundedCornerShape(1.dp), CARD_ART_PX)
        if (tape) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -(pad + 10.dp))
                    .size(width = cover * 0.34f, height = 16.dp)
                    .rotate(-5f)
                    .background(Champagne.copy(alpha = 0.55f)),
            )
        }
    }
}

@Composable
private fun HourRing(diameter: Dp, peakHour: Int?, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringPulse",
    )
    Canvas(modifier.size(diameter)) {
        val r = this.size.minDimension / 2f
        val c = center
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Champagne.copy(alpha = 0.22f), Color.Transparent),
                center = c,
                radius = r,
            ),
            radius = r,
        )
        // One tick per hour of the day, midnight at the top. The hour the
        // listening peaks at is the long gold one.
        for (i in 0 until RING_HOURS) {
            val a = (i / RING_HOURS.toFloat()) * 2f * PI.toFloat() - PI.toFloat() / 2f
            val peak = peakHour != null && i == peakHour
            val major = i % 6 == 0
            val len = if (peak) 24.dp.toPx() else if (major) 14.dp.toPx() else 8.dp.toPx()
            val outer = r - 2.dp.toPx()
            drawLine(
                color = if (peak) {
                    Champagne.copy(alpha = pulse)
                } else {
                    Color.White.copy(alpha = if (major) 0.40f else 0.20f)
                },
                start = Offset(c.x + cos(a) * (outer - len), c.y + sin(a) * (outer - len)),
                end = Offset(c.x + cos(a) * outer, c.y + sin(a) * outer),
                strokeWidth = if (peak) 3.2.dp.toPx() else 1.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun Vinyl(
    url: String?,
    diameter: Dp,
    modifier: Modifier = Modifier,
    spinning: Boolean = true,
) {
    val spin by rememberInfiniteTransition(label = "vinyl").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
        label = "vinylSpin",
    )
    Box(
        modifier.size(diameter).shadow(24.dp, CircleShape, clip = false),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = if (spinning) spin else 0f }
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2f
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF151518),
                            Color(0xFF2B2B31),
                            Color(0xFF111114),
                            Color(0xFF2E2E34),
                            Color(0xFF151518),
                        ),
                    ),
                    radius = r,
                )
                var g = r * 0.40f
                while (g < r * 0.97f) {
                    drawCircle(Color.White.copy(alpha = 0.055f), g, style = Stroke(0.7.dp.toPx()))
                    g += 3.4.dp.toPx()
                }
            }
            Cover(url, "", diameter * 0.34f, CircleShape, CARD_ART_PX)
            Box(Modifier.size(diameter * 0.05f).clip(CircleShape).background(Color(0xFF0B0B0D)))
        }
        // The light on the record stays put while the record turns under it.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.Transparent,
                        Color.White.copy(alpha = 0.07f),
                    ),
                    start = Offset.Zero,
                    end = Offset(this.size.width, this.size.height),
                ),
                radius = this.size.minDimension / 2f,
            )
        }
    }
}

@Composable
private fun HaloAvatar(url: String?, name: String, diameter: Dp) {
    val pulse by rememberInfiniteTransition(label = "halo").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "haloPhase",
    )
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = this.size.minDimension / 2f
            for (i in 0 until 3) {
                val t = (pulse + i / 3f) % 1f
                drawCircle(
                    Champagne.copy(alpha = (1f - t) * 0.34f),
                    base * (1.05f + t * 0.75f),
                    style = Stroke(1.2.dp.toPx()),
                )
            }
        }
        Cover(
            url = url,
            fallbackText = name,
            size = diameter,
            shape = CircleShape,
            px = HEADER_ART_PX,
            modifier = Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
            elevated = true,
        )
    }
}

@Composable
private fun EqualizerChart(rows: List<ReplayRow>, reveal: Animatable<Float, *>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val maxBar = maxHeight - 40.dp
        val top = (rows.firstOrNull()?.ms ?: 1L).toFloat().coerceAtLeast(1f)
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            rows.forEachIndexed { index, row ->
                val fraction = (row.ms.toFloat() / top).coerceIn(0.14f, 1f)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    EqBar(maxBar * fraction, index == 0, index, reveal)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        color = Color.White.copy(alpha = if (index == 0) 0.95f else 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.height(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EqBar(barHeight: Dp, strong: Boolean, order: Int, reveal: Animatable<Float, *>) {
    val bob by rememberInfiniteTransition(label = "eq$order").animateFloat(
        initialValue = 0.93f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(780 + order * 140, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "eqBob",
    )
    Box(
        Modifier
            .width(26.dp)
            .height(barHeight)
            .graphicsLayer {
                val t = ((reveal.value - 0.25f - order * 0.07f) / 0.45f).coerceIn(0f, 1f)
                val e = 1f - (1f - t) * (1f - t) * (1f - t)
                scaleY = e * bob
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (strong) 1f else 0.78f),
                        Champagne.copy(alpha = if (strong) 0.55f else 0.28f),
                    ),
                ),
            ),
    )
}

@Composable
private fun Ticket(
    value: String,
    label: String,
    number: Int,
    angle: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .rotate(angle)
            .shadow(12.dp, shape, clip = false)
            .clip(shape)
            .background(Paper)
            .drawBehind {
                val x = this.size.width * 0.78f
                drawLine(
                    color = Ink.copy(alpha = 0.35f),
                    start = Offset(x, 10.dp.toPx()),
                    end = Offset(x, this.size.height - 10.dp.toPx()),
                    strokeWidth = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                )
                drawCircle(Color(0x66000000), 7.dp.toPx(), Offset(x, 0f))
                drawCircle(Color(0x66000000), 7.dp.toPx(), Offset(x, this.size.height))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(0.78f).padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                fontFamily = FontFamily.Serif,
                fontSize = 30.sp,
                fontWeight = FontWeight.W800,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.66f),
            )
        }
        Column(
            Modifier.weight(0.22f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "NO.",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = Ink.copy(alpha = 0.5f),
            )
            Text(
                text = "%02d".format(number),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.W700,
                color = Ink.copy(alpha = 0.7f),
            )
        }
    }
}

// ── Pieces ──────────────────────────────────────────────────────────────────

@Composable
private fun Cover(
    url: String?,
    fallbackText: String,
    size: Dp,
    shape: Shape,
    px: Int,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val base = modifier
        .size(size)
        .let { if (elevated) it.shadow(18.dp, shape, clip = false) else it }
        .clip(shape)
    when {
        url != null -> AsyncImage(
            model = url.artworkAt(px),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = base,
        )
        fallbackText.isNotBlank() -> Box(base) { InitialTile(fallbackText, size, shape) }
        else -> Box(base.background(Color.White.copy(alpha = 0.10f)))
    }
}

/**
 * The scatter of covers and faces the opening cards are built around.
 *
 * Deliberately hand-placed rather than laid out. A grid of the top six looks
 * like a search result; the point of this is to look like a pile of records
 * someone has been through, which needs overlap, rotation and inconsistent
 * sizes — none of which any layout the framework offers will produce, and all of
 * which are stable here because the offsets are fractions of the box rather than
 * pixel positions.
 *
 * Artists come back as circles and releases as squares, which is the same
 * distinction every music app makes and the only label these need.
 */
@Composable
private fun ArtworkCollage(
    summary: ReplaySummary,
    reveal: Animatable<Float, *>,
    modifier: Modifier = Modifier,
) {
    val covers = remember(summary) {
        summary.songs.mapNotNull { it.song.thumbnailUrl }.distinct().take(3)
    }
    val faces = remember(summary) {
        summary.artists.mapNotNull { it.artworkUrl }.distinct()
            .filterNot { it in covers }
            .take(3)
    }
    if (covers.isEmpty() && faces.isEmpty()) return

    BoxWithConstraints(modifier) {
        val w = maxWidth
        val h = maxHeight
        // Sized from the smaller side, so the pile shrinks with the room the
        // card gives it instead of overflowing it. Anchors are fractions of the
        // box, chosen so every piece lands inside it.
        val unit = minOf(w, h * 1.05f)
        val squares = listOf(
            Triple(0.27f to 0.30f, unit * 0.50f, -4f),
            Triple(0.03f to 0.05f, unit * 0.30f, -10f),
            Triple(0.64f to 0.00f, unit * 0.27f, 8f),
        )
        val circles = listOf(
            Triple(0.02f to 0.64f, unit * 0.22f, 0f),
            Triple(0.72f to 0.38f, unit * 0.26f, 0f),
            Triple(0.46f to 0.74f, unit * 0.22f, 0f),
        )
        covers.forEachIndexed { index, url ->
            val (position, dim, angle) = squares[index]
            Polaroid(
                url = url,
                cover = dim,
                angle = angle,
                tape = index == 0,
                modifier = Modifier
                    .offset(x = w * position.first, y = h * position.second)
                    .staged(reveal, 0.10f + index * 0.12f, 0.5f, 36.dp),
            )
        }
        faces.forEachIndexed { index, url ->
            val (position, dim, _) = circles[index]
            Cover(
                url = url,
                fallbackText = "",
                size = dim,
                shape = CircleShape,
                px = CARD_ART_PX,
                modifier = Modifier
                    .offset(x = w * position.first, y = h * position.second)
                    .staged(reveal, 0.30f + index * 0.10f, 0.5f, 30.dp)
                    .border(2.5.dp, Paper, CircleShape),
                elevated = true,
            )
        }
    }
}

/** [this] with every colour turned [degrees] around the wheel, tone untouched. */
private fun MeshPalette.rotated(degrees: Float): MeshPalette {
    if (degrees == 0f) return this
    return MeshPalette(
        colors.map { color ->
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color.toArgb(), hsl)
            hsl[0] = (hsl[0] + degrees) % 360f
            Color(ColorUtils.HSLToColor(hsl))
        },
    )
}

/**
 * How much room [StoryChrome] takes above a card's content — the cross, the
 * segments and the two words, measured rather than guessed at because the
 * chrome is drawn over the pager and cannot push anything down.
 */
private val CHROME_HEIGHT = 96.dp

/** How far down a chart a card goes. */
private const val STORY_ROWS = 5

/**
 * The shape of a story card, everywhere. See [StoryFrame] for why it is fixed
 * rather than following the screen.
 */
private const val STORY_ASPECT = 9f / 16f

/** How long a card holds before moving on, unless a finger is on the screen. */
private const val PAGE_MILLIS = 14_000f

/** How long a tap or auto-advance takes to slide from one card to the next. */
private const val STEP_MILLIS = 420

private val Champagne = Color(0xFFEBD9A8)
private val Paper = Color(0xFFF4ECDD)
private val Ink = Color(0xFF2A2118)
private val GrainLight = Color.White.copy(alpha = 0.06f)
private val GrainDark = Color.Black.copy(alpha = 0.09f)

/** Fixed film grain: x, y as fractions of the card, then the speck's radius in dp. */
private val GrainDots: List<Triple<Float, Float, Float>> = run {
    val random = Random(26)
    List(420) { Triple(random.nextFloat(), random.nextFloat(), 0.4f + random.nextFloat() * 0.9f) }
}

/** Ticks on the Minutes card's ring: one per hour of the day. */
private const val RING_HOURS = 24

/** Each card's own atmosphere, drawn behind its content. Drawn once, never invalidated. */
private fun DrawScope.drawDecor(page: ReplayStoryPage) {
    val w = size.width
    val h = size.height
    val white = Color.White
    when (page) {
        ReplayStoryPage.INTRO -> {
            // A light leak off the corner and a film strip's perforations.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFC27A).copy(alpha = 0.40f), Color.Transparent),
                    center = Offset(w, 0f),
                    radius = w * 1.05f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF8FA3).copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(0f, h),
                    radius = w * 0.95f,
                ),
            )
            val hole = 8.dp.toPx()
            val gap = 30.dp.toPx()
            val holeW = hole * 0.7f
            var y = gap * 0.5f
            while (y < h) {
                drawRoundRect(
                    white.copy(alpha = 0.10f),
                    Offset(5.dp.toPx(), y),
                    Size(holeW, hole),
                    CornerRadius(2.dp.toPx()),
                )
                drawRoundRect(
                    white.copy(alpha = 0.10f),
                    Offset(w - 5.dp.toPx() - holeW, y),
                    Size(holeW, hole),
                    CornerRadius(2.dp.toPx()),
                )
                y += gap
            }
        }
        ReplayStoryPage.MINUTES -> {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(white.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.52f),
                    radius = w * 0.85f,
                ),
            )
        }
        ReplayStoryPage.SONGS -> {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(white.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * 0.5f, 0f),
                    radius = w * 0.95f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Champagne.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(w, h),
                    radius = w * 0.9f,
                ),
            )
        }
        ReplayStoryPage.ARTISTS -> {
            val tints = listOf(Color(0xFFFFD6A5), Color(0xFFA5C8FF))
            for (side in 0..1) {
                val x0 = if (side == 0) w * 0.12f else w * 0.88f
                val beam = Path().apply {
                    moveTo(x0 - 10.dp.toPx(), 0f)
                    lineTo(x0 + 10.dp.toPx(), 0f)
                    lineTo(w * 0.5f + w * 0.26f, h * 0.72f)
                    lineTo(w * 0.5f - w * 0.26f, h * 0.72f)
                    close()
                }
                drawPath(
                    beam,
                    Brush.verticalGradient(
                        listOf(tints[side].copy(alpha = 0.18f), Color.Transparent),
                        startY = 0f,
                        endY = h * 0.72f,
                    ),
                )
            }
        }
        ReplayStoryPage.ALBUMS -> {
            // The grooves of a record too big for the card, bleeding off its corner.
            val c = Offset(w * 1.02f, h * 0.9f)
            var r = 22.dp.toPx()
            while (r < w * 1.1f) {
                drawCircle(white.copy(alpha = 0.07f), r, c, style = Stroke(0.8.dp.toPx()))
                r += 13.dp.toPx()
            }
        }
        ReplayStoryPage.GENRES -> {
            // Halftone dots fading out from the top-left, like a pop poster.
            val step = 24.dp.toPx()
            var row = 0
            var y = 0f
            while (y < h) {
                var x = if (row % 2 == 0) 0f else step / 2f
                while (x < w) {
                    val d = (x + (h - y) * 0.6f) / (w + h * 0.6f)
                    val radius = step * 0.36f * (1f - d).coerceIn(0f, 1f)
                    if (radius > 0.6f) drawCircle(white.copy(alpha = 0.10f), radius, Offset(x, y))
                    x += step
                }
                y += step * 0.86f
                row++
            }
        }
        ReplayStoryPage.HABITS -> {
            // Ruled diary paper with a red margin.
            val gap = 34.dp.toPx()
            var y = h * 0.26f
            while (y < h) {
                drawLine(white.copy(alpha = 0.07f), Offset(0f, y), Offset(w, y), 1.dp.toPx())
                y += gap
            }
            drawLine(
                AccentRed.copy(alpha = 0.26f),
                Offset(w * 0.12f, 0f),
                Offset(w * 0.12f, h),
                1.2.dp.toPx(),
            )
        }
        ReplayStoryPage.SUMMARY -> {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFF2D9A0).copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.12f),
                    radius = w * 0.9f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Champagne.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(w * 0.5f, h),
                    radius = w,
                ),
            )
        }
    }
}

/** The same darkened edge and film grain on every card, so the run reads as one reel. */
private fun DrawScope.drawVignetteAndGrain() {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
            center = center,
            radius = size.maxDimension * 0.78f,
        ),
    )
    val w = size.width
    val h = size.height
    GrainDots.forEachIndexed { index, dot ->
        drawCircle(
            color = if (index % 2 == 0) GrainLight else GrainDark,
            radius = dot.third.dp.toPx(),
            center = Offset(w * dot.first, h * dot.second),
        )
    }
}

/** The share of the width that means "back" — the left edge, as everywhere else. */
private const val BACK_ZONE = 0.32f

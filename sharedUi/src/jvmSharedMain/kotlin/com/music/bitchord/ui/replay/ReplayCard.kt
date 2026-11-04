package com.music.bitchord.ui.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxWidth
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.player.renderEffectBlurSupported
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.rememberArtworkColors
import java.util.Locale

/**
 * One headline number, as a card you could keep in a wallet.
 *
 * ## Why a card
 *
 * A Replay's four headline facts — minutes, artist, song, album — are four
 * unlike things, and a grid of tiles gives no clue that they are peers or that
 * any of them leads anywhere. A card is a thing people already know how to read:
 * it holds one fact, it has a *front* you look at rather than a list you scan,
 * and a row of them says "there are four of these" without a heading saying so.
 * Tapping one opens Replay at the chart it summarises, which is the other half
 * of why it is a card — a card is an object, and objects can be picked up.
 *
 * ## Why it is built like a real one
 *
 * Every part of the anatomy is doing the job its real counterpart does, which is
 * what stops this reading as a rounded rectangle with a chip sticker on it:
 *
 *  - the **proportions** are the real ones, 85.6 × 54mm, so 1.586:1 — this alone
 *    accounts for most of the recognition;
 *  - the **cardholder line and member-since date** are the part that makes it
 *    *yours*. A card with nobody's name on it is a graphic; with a name on it,
 *    it is an artefact of an account, which is exactly what a year of listening
 *    is. There is no chip: a contact plate is the one piece of card anatomy
 *    that exists to be *inserted into something*, and on a card that is only
 *    ever looked at it reads as a sticker;
 *  - the **embossing** — a monospaced face, wide tracking, a dark offset shadow
 *    under a pale gradient fill — is how a real card's raised type catches the
 *    light, and it is why those lines read as pressed into the card rather than
 *    printed on it;
 *  - the **logo sits top-right**, opposite what the card is for, so the two
 *    corners of the head each carry one fact and neither is a caption for the
 *    other.
 *
 * ## Why the background is the player's own mesh
 *
 * Sampled from the artwork this card is about, so no two people's cards look
 * alike and each one is lit by the record it is describing. It is drawn once
 * and held still — [MeshGradientBackground]'s `animated = false` — rather
 * than crossfading or drifting, since a row of these redrawing a blurred
 * layer every time a card is opened or swiped past is the expensive case the
 * class note there warns about, multiplied by however many cards are on
 * screen.
 */
@Composable
fun ReplayCreditCard(
    label: String,
    value: String,
    detail: String?,
    artworkUrl: String?,
    /** Whose card it is. Empty falls back to the localized listener label. */
    holder: String,
    /** `MM/YY`, or null when there is nothing to date it from. */
    memberSince: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artUrl = artworkUrl.artworkAt(CARD_ART_PX)
    Box(
        modifier = modifier
            .aspectRatio(CARD_RATIO)
            .shadow(16.dp, CardShape, clip = false)
            .clip(CardShape)
            .clickable(onClick = onClick),
    ) {
        if (artUrl != null) {
            CardArtwork(url = artUrl)
        } else {
            val palette = rememberArtworkColors(artworkUrl)
            MeshGradientBackground(
                palette = palette,
                trackKey = artworkUrl ?: label,
                blurRadius = 34.dp,
                animated = false,
            )
        }
        // Deepened towards the foot, where the embossed lines are: the mesh is
        // built to be bright and those lines are pale, and without this the
        // cardholder name lands on whichever blob happened to drift under it.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.46f),
                        0.35f to Color.Black.copy(alpha = 0.22f),
                        0.60f to Color.Black.copy(alpha = 0.52f),
                        1.0f to Color.Black.copy(alpha = 0.84f),
                    ),
                ),
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
            // Head: what the card is on the left, in a dark pill so it stays
            // readable over any artwork; the logo on the right.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.your_listening_experience).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = 1.1.sp,
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.32f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(Res.drawable.ic_logo),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(width = 30.dp, height = 19.dp),
                )
            }

            // Body: takes only the height left over, so the foot below is never
            // pushed off the card. The figure is big only when it is short.
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Column {
                    val short = value.length <= 6
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = if (short) 34.sp else 17.sp,
                            lineHeight = if (short) 38.sp else 21.sp,
                            fontWeight = if (short) FontWeight.W800 else FontWeight.W700,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.45f),
                                offset = Offset(0f, 2f),
                                blurRadius = 8f,
                            ),
                        ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = label.uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = 1.4.sp,
                        color = Color.White.copy(alpha = 0.80f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.20f)))
            Spacer(Modifier.height(8.dp))

            // Foot: whose card it is on the left, since when on the right.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = holder.ifBlank { stringResource(Res.string.default_replay_holder) },
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.W700,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail != null) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = Color.White.copy(alpha = 0.70f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (memberSince != null) {
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(Res.string.member_since).uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 7.sp,
                            lineHeight = 9.sp,
                            letterSpacing = 0.8.sp,
                            color = Color.White.copy(alpha = 0.60f),
                            textAlign = TextAlign.End,
                        )
                        Embossed(text = memberSince, size = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * A line pressed into the card rather than printed on it.
 *
 * Monospaced and widely tracked because that is what a card embosser produces —
 * fixed-pitch dies on a fixed-pitch wheel — and it is more of the recognition
 * than the shadow is. The shadow supplies the rest: dark, offset down, under a
 * fill that is brightest at the top edge, which is a raised surface lit from
 * above.
 */
@Composable
private fun Embossed(text: String, size: TextUnit) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.W600,
            fontSize = size,
            letterSpacing = 1.6.sp,
            brush = PolishedInk,
            shadow = EmbossShadow,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * A rank badge. Number one is the accent; the rest are quiet, because a chart
 * where every position shouts has no first place.
 */
@Composable
fun RankBadge(rank: Int, accent: Color, modifier: Modifier = Modifier) {
    Text(
        text = rank.toString(),
        style = if (rank == 1) {
            MaterialTheme.typography.titleLarge
        } else {
            MaterialTheme.typography.titleMedium
        },
        fontWeight = FontWeight.W800,
        color = if (rank == 1) accent else Color.White.copy(alpha = 0.45f),
        modifier = modifier.width(28.dp),
    )
}

/**
 * A stand-in cover for a row that has no artwork — a genre, which is a word
 * rather than a release. Its initial on a colour derived from the word itself,
 * so the same genre is the same colour every time the page is opened.
 */
@Composable
fun InitialTile(text: String, size: Dp, shape: Shape) {
    val hue = (text.hashCode().toFloat() % 360f + 360f) % 360f
    val color = Color.hsl(hue, 0.55f, 0.45f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.take(1).uppercase(Locale.ROOT),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.W800,
            color = Color.White,
        )
    }
}

/** The fill on every raised line: bright along the top edge, cooler below. */
private val PolishedInk = Brush.verticalGradient(
    listOf(Color(0xFFFFFFFF), Color(0xFFF3F4F8), Color(0xFFC9CCD6)),
)

/** What makes the fill above read as raised rather than merely pale. */
private val EmbossShadow = Shadow(
    color = Color(0x99000000),
    offset = Offset(0f, 2.5f),
    blurRadius = 4f,
)

private val CardShape = RoundedCornerShape(20.dp)

/**
 * Gambar utama card: artwork top 1 penuh satu card, dengan lapisan blur kedua
 * yang dimask gradien sehingga makin kuat ke arah bawah. Tempat teks
 * tertulis jadi tetap terbaca tanpa menutup gambar di bagian atas.
 */
@Composable
private fun CardArtwork(url: String) {
    Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E))) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (renderEffectBlurSupported) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.30f to Color.Transparent,
                                0.75f to Color.Black,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .blur(28.dp),
            )
        }
    }
}

/** Whose card it is when there is no signed-in account to name. */

/** 85.6mm × 54mm, which is what makes the shape read as a card. */
private const val CARD_RATIO = 1.586f

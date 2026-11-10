package com.music.zenith.ui.replay

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
import com.music.zenith.data.model.CARD_ART_PX
import com.music.zenith.data.model.artworkAt
import com.music.zenith.ui.player.renderEffectBlurSupported
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
import com.music.zenith.sharedui.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.music.zenith.ui.player.MeshGradientBackground
import com.music.zenith.ui.player.rememberArtworkColors
import java.util.Locale

/**
 * One headline number, set the way Apple Music sets a Replay: the artwork
 * edge to edge, a small label at the top and the figure large at the foot,
 * with nothing else on the card. Tapping it opens Replay at the chart it
 * summarises.
 *
 * Legibility comes from a single gradient scrim over the artwork rather than
 * a blurred second layer. Without artwork the player's own mesh is drawn once
 * and held still.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun ReplayCreditCard(
    label: String,
    value: String,
    detail: String?,
    artworkUrl: String?,
    /** Kept so callers stay as they are; the card no longer prints a cardholder line. */
    holder: String,
    /** Kept so callers stay as they are; the card no longer prints a date. */
    memberSince: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artUrl = artworkUrl.artworkAt(CARD_ART_PX)
    Box(
        modifier = modifier
            .aspectRatio(CARD_RATIO)
            .shadow(6.dp, CardShape, clip = false)
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
        // One scrim: a little dark at the top for the label, more at the foot for the figure.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.28f),
                        0.40f to Color.Black.copy(alpha = 0.04f),
                        1.0f to Color.Black.copy(alpha = 0.66f),
                    ),
                ),
        )

        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 13.sp,
                fontWeight = FontWeight.W600,
                color = Color.White.copy(alpha = 0.90f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            val short = value.length <= 6
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = if (short) 44.sp else 20.sp,
                    lineHeight = if (short) 46.sp else 24.sp,
                    fontWeight = FontWeight.W700,
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
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

private val CardShape = RoundedCornerShape(20.dp)

/**
 * Gambar utama card: artwork top 1 penuh satu card, apa adanya. Keterbacaan
 * teks dijaga satu scrim gradien di [ReplayCreditCard], bukan blur berlapis.
 */
@Composable
private fun CardArtwork(url: String) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().background(Color(0xFF1C1C1E)),
    )
}

/** Whose card it is when there is no signed-in account to name. */

/** 85.6mm × 54mm, which is what makes the shape read as a card. */
private const val CARD_RATIO = 1.586f

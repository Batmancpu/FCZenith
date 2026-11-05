package com.music.zenith.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.zenith.ui.player.MeshGradientBackground
import com.music.zenith.ui.player.MeshPalette

/*
 * Kartu "Made for You Mix" di Top Picks: latar blob cairan (warna per mix) dan
 * teks ala sampul Apple Music: wordmark kecil di kiri atas, nama mix besar dua
 * baris di bawahnya ("Chill" / "Mix"), dan satu baris artis di kaki kartu.
 */

/** Latar cairan; Android memasok AGSL lewat [LocalMixCoverBackground], lainnya mesh gradient. */
@Composable
internal fun MixFluidArt(title: String, modifier: Modifier = Modifier) {
    val palette = remember(title) { mixPalette(title) }
    val background = LocalMixCoverBackground.current
    val motion = LocalArtworkMotion.current
    if (background != null) {
        background(palette, motion, modifier)
    } else {
        MeshGradientBackground(
            palette = remember(palette) { MeshPalette(palette.take(4)) },
            modifier = modifier,
            trackKey = title,
            blurRadius = 32.dp,
        )
    }
}

/** Teks di atas [MixFluidArt]; bayangan lembut menjaga keterbacaan di atas warna terang. */
@Composable
internal fun MixCoverOverlay(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val (name, tail) = remember(title) { splitMixTitle(title) }
    val style = MaterialTheme.typography.headlineMedium.copy(
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp,
        shadow = Shadow(Color(0x45000000), Offset(0f, 2f), 12f),
    )
    Box(modifier) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.34f)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.24f))),
                ),
        )
        Wordmark(
            logoHeight = 12.dp,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.92f),
            gap = 2.dp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 18.dp, top = 18.dp),
        )
        Column(Modifier.align(Alignment.TopStart).padding(start = 18.dp, end = 18.dp, top = 46.dp)) {
            Text(
                text = name,
                color = Color.White,
                style = style.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (tail != null) {
                Text(
                    text = tail,
                    color = Color.White.copy(alpha = 0.92f),
                    style = style.copy(fontWeight = FontWeight.Normal),
                    maxLines = 1,
                )
            }
        }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    shadow = Shadow(Color(0x40000000), Offset(0f, 1f), 6f),
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
            )
        }
    }
}
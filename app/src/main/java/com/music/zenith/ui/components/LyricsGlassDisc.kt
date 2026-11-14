package com.music.zenith.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.zenith.data.settings.AppSettings

/**
 * Permukaan kaca untuk tombol bulat kontrol lirik, mengikuti pengaturan glass
 * app seperti tombol back melayang: aktif hanya bila Liquid Glass hidup dan
 * didukung, memakai nilai tint / rim / shadow dari pengaturan glass, plus rim
 * dan hairline yang sama.
 *
 * Tombol ini berada di atas player, yang bukan bagian rekaman [LocalAppBackdrop]
 * (itu merekam halaman di bawah player), jadi tidak ada sampling backdrop di
 * sini: fill putih tipis, rim, dan hairline. Dengan begitu tidak ada lagi
 * lingkaran hitam. Mengembalikan null bila kaca mati / tidak didukung / Reduce
 * dynamic blur aktif, supaya pemanggil memakai disc bawaannya.
 */
@Composable
fun lyricsGlassDiscSurface(active: Boolean): Modifier? {
    val glassOn = LocalLiquidGlassEnabled.current && isGlassSupported()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val custom by AppSettings.glassCustom.collectAsStateWithLifecycle()
    val lift by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "lyricsGlassDiscActive",
    )
    if (!glassOn || reduceDynamicBlur) return null

    val shape = CircleShape
    val fillAlpha = ((0.12f + 0.12f * lift) * custom.tint).coerceIn(0f, 1f)
    val shadowAlpha = (0.20f * custom.shadow).coerceIn(0f, 1f)
    return Modifier
        .shadow(
            elevation = 6.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = shadowAlpha * 0.6f),
            spotColor = Color.Black.copy(alpha = shadowAlpha),
        )
        .clip(shape)
        .background(Color.White.copy(alpha = fillAlpha), shape)
        .liquidGlassRim(shape)
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}
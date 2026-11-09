package com.music.zenith.ui.components.floatingtabbar

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Pinggang karet: bagian tengah bar menyempit saat ditarik (pinch > 0) dan
 * menggembung saat memantul lewat titik awal (pinch < 0). Tepi bawah tetap.
 */
private const val RUBBER_AGSL = """
uniform shader content;
uniform float2 size;
uniform float pinch;
half4 main(float2 coord) {
    float2 uv = coord / size;
    float waist = sin(clamp(uv.y, 0.0, 1.0) * 3.14159265);
    float cx = size.x * 0.5;
    float x = cx + (coord.x - cx) * (1.0 + pinch * waist);
    return content.eval(float2(x, coord.y));
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object RubberShader {
    val shader = RuntimeShader(RUBBER_AGSL)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun rubberEffect(width: Float, height: Float, p: Float): RenderEffect {
    val shader = RubberShader.shader
    shader.setFloatUniform("size", width, height)
    shader.setFloatUniform("pinch", 0.22f * p)
    return AndroidRenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
}

/**
 * Tarikan karet pada bar: 0 = diam, positif = tertarik ke atas dan memipih,
 * negatif = memantul dan menggembung. Dibaca di graphicsLayer saja, jadi satu
 * frame animasi tidak merekomposisi apa pun.
 */
internal fun Modifier.rubberStretch(liftPx: Float, stretch: () -> Float): Modifier = graphicsLayer {
    val p = stretch()
    transformOrigin = TransformOrigin(0.5f, 1f)
    translationY = -liftPx * p
    scaleY = 1f + 0.10f * p
    scaleX = 1f - 0.04f * p
    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && p != 0f) {
        rubberEffect(size.width, size.height, p)
    } else {
        null
    }
}
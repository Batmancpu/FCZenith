package com.motion.lyrics

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

@RequiresApi(33)
internal object BloomAgsl {
    private const val SRC = """
uniform shader content;
uniform float2 layerSize;
uniform float headX;
uniform float flowDir;
uniform float bloom;
uniform float bloomR;
uniform float bloomLag;
uniform float bloomSpan;

half4 main(float2 p) {
    half4 c = content.eval(p);
    float d = (headX - bloomLag * flowDir - p.x) * flowDir;
    float wFront = smoothstep(-bloomSpan * 0.35, 0.0, d);
    float wTrail = 1.0 - smoothstep(0.0, bloomSpan, d);
    float edge = smoothstep(0.0, bloomR,
        min(min(p.x, layerSize.x - p.x), min(p.y, layerSize.y - p.y)));
    float k = bloom * wFront * wTrail * edge;
    if (k <= 0.002) return c;
    half4 acc = half4(0.0);
    for (int i = 0; i < 12; i++) {
        float a = 0.5235988 * float(i);
        float r = bloomR * (0.45 + 0.55 * mod(float(i), 2.0));
        acc += content.eval(p + float2(cos(a), sin(a)) * r);
    }
    acc *= (1.0 / 12.0);
    return c + acc * half(k) * (1.0 - c.a);
}
"""

    /** Dikompilasi sekali per baris lalu dipakai ulang. */
    fun newShader(): RuntimeShader = RuntimeShader(SRC)

    fun effect(
        shader: RuntimeShader,
        w: Float, h: Float,
        headX: Float, flow: Float, bloom: Float,
        radius: Float, lag: Float, span: Float
    ): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("layerSize", w, h)
        shader.setFloatUniform("headX", headX)
        shader.setFloatUniform("flowDir", flow)
        shader.setFloatUniform("bloom", bloom)
        shader.setFloatUniform("bloomR", radius)
        shader.setFloatUniform("bloomLag", lag)
        shader.setFloatUniform("bloomSpan", span)
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
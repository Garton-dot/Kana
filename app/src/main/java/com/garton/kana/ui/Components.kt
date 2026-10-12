package com.garton.kana.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun Ring(progress: Float, size: Dp, color: Color, stroke: Dp = 8.dp, content: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(size),
            color = color,
            strokeWidth = stroke,
            trackColor = color.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
        )
        content()
    }
}

/** Stars that pop in one after another when [animate] is set. */
@Composable
fun StarRow(stars: Int, size: Int = 18, animate: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) { i ->
            val scale = remember { Animatable(if (animate) 0f else 1f) }
            if (animate) {
                LaunchedEffect(Unit) {
                    delay(300L + i * 350L)
                    scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow))
                }
            }
            Text(
                "★",
                fontSize = size.sp,
                color = if (i < stars) KanaColors.Gold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.scale(if (i < stars) scale.value else 1f),
            )
        }
    }
}

private class Particle(val x: Float, val y: Float, val vx: Float, val vy: Float, val spin: Float, val size: Float, val color: Color)

private val CONFETTI = listOf(KanaColors.Sakura, KanaColors.Gold, KanaColors.Indigo, KanaColors.Correct, KanaColors.Ice, KanaColors.Flame)

@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val particles = remember {
        List(140) {
            Particle(
                x = Random.nextFloat(), y = -Random.nextFloat() * 0.6f,
                vx = (Random.nextFloat() - 0.5f) * 0.25f, vy = 0.15f + Random.nextFloat() * 0.3f,
                spin = Random.nextFloat() * 720f - 360f, size = 10f + Random.nextFloat() * 14f,
                color = CONFETTI[it % CONFETTI.size],
            )
        }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (time < 5f) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }
    Canvas(modifier) {
        for (p in particles) {
            val x = (p.x + p.vx * time) * size.width
            val y = (p.y + p.vy * time + 0.08f * time * time) * size.height
            if (y > size.height) continue
            rotate(p.spin * time, Offset(x, y)) {
                drawRect(p.color, topLeft = Offset(x, y), size = Size(p.size, p.size * 0.55f))
            }
        }
    }
}

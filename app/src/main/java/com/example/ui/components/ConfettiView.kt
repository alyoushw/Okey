package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.random.Random

data class ConfettiParticle(
    val xRatio: Float,
    val startY: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val rotationSpeed: Float,
    val wobbleFreq: Float
)

@Composable
fun ConfettiCelebration(
    modifier: Modifier = Modifier
) {
    val anim = remember { Animatable(0f) }

    val particles = remember {
        val colors = listOf(
            Color(0xFFE91E63),
            Color(0xFF2196F3),
            Color(0xFFFFC107),
            Color(0xFF4CAF50),
            Color(0xFFFF5722),
            Color(0xFF9C27B0),
            Color(0xFF00BCD4)
        )
        List(65) {
            ConfettiParticle(
                xRatio = Random.nextFloat(),
                startY = -Random.nextFloat() * 400f,
                speed = 400f + Random.nextFloat() * 700f,
                size = 14f + Random.nextFloat() * 16f,
                color = colors[Random.nextInt(colors.size)],
                rotationSpeed = (Random.nextFloat() - 0.5f) * 720f,
                wobbleFreq = 2f + Random.nextFloat() * 4f
            )
        }
    }

    LaunchedEffect(Unit) {
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 4000, easing = LinearEasing)
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val progress = anim.value
        val canvasWidth = size.width
        val canvasHeight = size.height

        for (p in particles) {
            val y = p.startY + progress * (canvasHeight + 600f) * (p.speed / 600f)
            if (y in -50f..(canvasHeight + 50f)) {
                val wobble = kotlin.math.sin(progress * 10f * p.wobbleFreq).toFloat() * 25f
                val x = (p.xRatio * canvasWidth) + wobble
                val angle = progress * p.rotationSpeed

                rotate(degrees = angle, pivot = Offset(x, y)) {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(x - p.size / 2, y - p.size / 2),
                        size = Size(p.size, p.size * 0.6f)
                    )
                }
            }
        }
    }
}

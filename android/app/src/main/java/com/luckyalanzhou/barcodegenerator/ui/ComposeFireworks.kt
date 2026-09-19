package com.luckyalanzhou.barcodegenerator.ui

import com.luckyalanzhou.barcodegenerator.*

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

/** Compose Canvas 版烟花层，保留原彩蛋的自动发射和触摸发射行为。 */
@Composable
internal fun ComposeFireworksOverlay() {
    val rockets = remember { mutableStateListOf<ComposeRocket>() }
    val bursts = remember { mutableStateListOf<ComposeBurst>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var frameTick by remember { mutableLongStateOf(0L) }
    var hintVisible by remember { mutableStateOf(true) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val colors = remember {
        intArrayOf(0xffff718d.toInt(), 0xffffc75a.toInt(), 0xff78d7ff.toInt(), 0xffc09cff.toInt(), 0xff80e7a5.toInt())
    }

    fun launch(targetX: Float, targetY: Float, color: Int) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0) return
        val launchX = (targetX + ((targetX / canvasSize.width - .5f) * -46f * density))
            .coerceIn(18f * density, canvasSize.width - 18f * density)
        val style = ((targetX.toInt() * 31) xor (targetY.toInt() * 17) xor System.nanoTime().toInt()).and(7)
        rockets += ComposeRocket(
            launchX,
            canvasSize.height - 24f * density,
            targetX,
            targetY,
            color,
            density,
            style,
        )
        while (rockets.size > 5) rockets.removeAt(0)
    }

    LaunchedEffect(canvasSize) {
        if (canvasSize == IntSize.Zero) return@LaunchedEffect
        var previousFrame = 0L
        var lastAutoLaunch = 0L
        val hintUntil = System.nanoTime() + 3_500_000_000L
        while (isActive) {
            withFrameNanos { frame ->
                val delta = if (previousFrame == 0L) 0f
                else ((frame - previousFrame).coerceIn(0L, 33_000_000L) / 1_000_000_000f)
                previousFrame = frame
                if (frame - lastAutoLaunch >= 1_350_000_000L) {
                    val number = (frame / 1_350_000_000L).toInt()
                    launch(
                        canvasSize.width * (.14f + (number % 6) * .145f),
                        canvasSize.height * (.18f + (number % 4) * .13f),
                        colors[number.mod(colors.size)],
                    )
                    lastAutoLaunch = frame
                }
                rockets.toList().forEach { rocket ->
                    rocket.update(delta)
                    if (rocket.finished) bursts += ComposeBurst(rocket.x, rocket.y, rocket.color, density, rocket.style)
                }
                rockets.removeAll { it.finished }
                bursts.forEach { it.update(delta) }
                bursts.removeAll { it.finished }
                hintVisible = System.nanoTime() < hintUntil
                frameTick = frame
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { canvasSize = it }
            .pointerInput(canvasSize) {
                detectTapGestures { offset ->
                    val index = ((offset.x.toInt() + offset.y.toInt()) / 41).mod(colors.size)
                    launch(offset.x, offset.y.coerceIn(canvasSize.height * .14f, canvasSize.height * .76f), colors[index])
                    hintVisible = false
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            frameTick
            rockets.forEach { it.draw(this) }
            bursts.forEach { it.draw(this) }
        }
        if (hintVisible) {
            Text(
                text = "轻触夜空，从地面发射一朵烟花",
                color = Color(0xffebf6ff),
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 44.dp),
            )
        }
    }
}

private class ComposeRocket(
    private val startX: Float,
    private val startY: Float,
    private val targetX: Float,
    private val targetY: Float,
    val color: Int,
    private val density: Float,
    val style: Int,
) {
    var x = startX
    var y = startY
    private var progress = 0f
    var finished = false
        private set

    fun update(delta: Float) {
        progress += delta / .7f
        if (progress >= 1f) {
            progress = 1f
            finished = true
        }
        x = startX + (targetX - startX) * progress
        y = startY + (targetY - startY) * progress
    }

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
        with(scope) {
            drawLine(
                color = Color.White.copy(alpha = .53f),
                start = Offset(x, y + 18f * density),
                end = Offset(x - (targetX - startX) * .04f, y + 42f * density),
                strokeWidth = 1.5f * density,
            )
            drawCircle(argbColor(color), 2.8f * density, Offset(x, y))
        }
    }
}

private class ComposeBurst(
    private val originX: Float,
    private val originY: Float,
    private val color: Int,
    density: Float,
    private val style: Int,
) {
    private val dots = Array(if (style == 4 || style == 6) 72 else 56) { index ->
        val dotCount = if (style == 4 || style == 6) 72 else 56
        val angle = Math.PI * 2.0 * index / dotCount
        val speed = when (style) {
            0 -> 74f + (index % 4) * 3f
            1 -> 42f + (index % 8) * 12f
            2 -> 58f + (index % 6) * 11f
            3 -> if (index % 7 == 0) 142f else 48f
            4 -> if (index % 3 == 0) 118f else 62f + (index % 5) * 9f
            5 -> 7f
            6 -> 64f + (index % 4) * 15f
            else -> 44f + (index % 7) * 10f
        } * density
        val heartX = (16.0 * sin(angle) * sin(angle) * sin(angle)).toFloat()
        val heartY = (-(13.0 * cos(angle) - 5.0 * cos(2.0 * angle) - 2.0 * cos(3.0 * angle) - cos(4.0 * angle))).toFloat()
        when (style) {
            5 -> ComposeDot(heartX * 7.2f * density, heartY * 7.2f * density, false)
            6 -> ComposeDot(cos(angle).toFloat() * speed * .62f, sin(angle).toFloat() * speed * .92f, true)
            7 -> ComposeDot(cos(angle).toFloat() * speed * .7f, sin(angle).toFloat() * speed, true)
            else -> ComposeDot(cos(angle).toFloat() * speed, sin(angle).toFloat() * speed, style == 2)
        }
    }
    private var age = 0f
    var finished = false
        private set

    fun update(delta: Float) {
        age += delta
        dots.forEach { dot ->
            dot.x += dot.vx * delta
            dot.y += dot.vy * delta
            dot.vy += if (dot.willow) 60f * delta else 38f * delta
            dot.vx *= if (dot.willow) .982f else .991f
            dot.vy *= if (dot.willow) .982f else .991f
        }
        finished = age > if (dots.first().willow || style == 7) 2.05f else 1.45f
    }

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
        with(scope) {
            val life = if (dots.first().willow || style == 7) 2.05f else 1.45f
            val alpha = (1f - age / life).coerceIn(0f, 1f)
            dots.forEachIndexed { index, dot ->
                val dotColor = if (style == 7 && index % 3 != 0) 0xffffd66b.toInt() else color
                val radius = if (style == 4 && index % 3 == 0) 2.35f else 1.55f + index % 3 * .38f
                drawCircle(argbColor(dotColor, alpha), radius, androidx.compose.ui.geometry.Offset(originX + dot.x, originY + dot.y))
            }
        }
    }

    private data class ComposeDot(
        var vx: Float,
        var vy: Float,
        val willow: Boolean,
        var x: Float = 0f,
        var y: Float = 0f,
    )
}

private fun argbColor(value: Int, alpha: Float = 1f): Color = Color(
    red = ((value shr 16) and 0xff) / 255f,
    green = ((value shr 8) and 0xff) / 255f,
    blue = (value and 0xff) / 255f,
    alpha = alpha,
)

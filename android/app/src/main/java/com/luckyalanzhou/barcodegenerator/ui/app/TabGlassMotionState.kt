package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/** One position owner for taps and drags; layer reads animate without recomposing tab content. */
@Stable
internal class TabGlassMotionState(
    private val scope: CoroutineScope,
    initialIndex: Int,
    private val tabCount: Int,
) {
    private val position = Animatable(initialIndex.toFloat())
    val impact = Animatable(0f)
    val contactSpread = Animatable(1f)
    var dragging by mutableStateOf(false)
        private set
    var settling by mutableStateOf(false)
        private set
    var pressed by mutableStateOf(false)
        private set
    var direction by mutableFloatStateOf(1f)
        private set
    var touchX by mutableFloatStateOf(Float.NaN)
        private set
    var touchY by mutableFloatStateOf(Float.NaN)
        private set
    private var fingerProgress by mutableFloatStateOf(initialIndex.toFloat())
    private var generation = 0
    private var requestedTarget = initialIndex
    private var positionJob: Job? = null
    private var impactJob: Job? = null
    private var contactJob: Job? = null
    private var previousEventMillis = 0L
    private var fingerVelocity = 0f
    private var reduceMotion = false

    fun setReducedMotion(value: Boolean) {
        if (value == reduceMotion) return
        reduceMotion = value
        if (value) {
            impactJob?.cancel()
            contactJob?.cancel()
            scope.launch { impact.snapTo(0f); contactSpread.snapTo(1f) }
            if (!dragging) animatePosition(requestedTarget, fromFinger = false)
        }
    }

    val progress: Float
        get() = (if (dragging) fingerProgress else position.value).coerceIn(0f, (tabCount - 1).toFloat())

    /** Tabs/second; release uses the spring's current velocity, never event counts. */
    val velocity: Float
        get() = if (reduceMotion) 0f else if (dragging) fingerVelocity else position.velocity

    fun press(point: Offset) {
        pressed = true
        updateTouch(point)
        if (reduceMotion) return
        contactJob?.cancel()
        contactJob = scope.launch {
            contactSpread.snapTo(0f)
            contactSpread.animateTo(1f, tween(280))
        }
    }

    fun updateTouch(point: Offset) {
        touchX = point.x
        touchY = point.y
    }

    fun endPress() {
        pressed = false
    }

    fun beginDrag(point: Offset, timeMillis: Long) {
        val start = progress
        generation += 1
        positionJob?.cancel()
        fingerProgress = start
        dragging = true
        settling = false
        previousEventMillis = timeMillis
        fingerVelocity = 0f
        updateTouch(point)
    }

    fun drag(deltaTabs: Float, point: Offset, timeMillis: Long) {
        val elapsed = timeMillis - previousEventMillis
        if (elapsed > 0) {
            val sample = tabDragVelocity(deltaTabs, elapsed)
            val weight = (1.0 - exp(-elapsed / 24.0)).toFloat()
            fingerVelocity += (sample - fingerVelocity) * weight
            previousEventMillis = timeMillis
        }
        if (abs(deltaTabs) > .0005f) direction = if (deltaTabs > 0f) 1f else -1f
        fingerProgress = (fingerProgress + deltaTabs).coerceIn(0f, (tabCount - 1).toFloat())
        updateTouch(point)
    }

    fun select(index: Int) {
        val target = index.coerceIn(0, tabCount - 1)
        if (dragging) {
            requestedTarget = target
            return
        }
        if (requestedTarget == target && positionJob?.isActive == true) return
        if (abs(target - progress) > .001f) direction = if (target > progress) 1f else -1f
        if (abs(target - progress) <= .001f && positionJob?.isActive != true) return
        animatePosition(target, fromFinger = false)
    }

    fun release(index: Int) = animatePosition(index.coerceIn(0, tabCount - 1), fromFinger = true)

    fun pulse(amount: Float) {
        if (reduceMotion) return
        impactJob?.cancel()
        impactJob = scope.launch {
            impact.snapTo(amount.coerceIn(0f, .035f))
            impact.animateTo(0f, spring(dampingRatio = .82f, stiffness = 820f))
        }
    }

    private fun animatePosition(target: Int, fromFinger: Boolean) {
        val start = progress
        val velocity = if (fromFinger) fingerVelocity else position.velocity
        val ticket = ++generation
        requestedTarget = target
        positionJob?.cancel()
        settling = true
        positionJob = scope.launch {
            try {
                if (fromFinger) {
                    // Hand over at the exact rendered finger position before exposing the spring.
                    position.snapTo(start)
                    if (ticket != generation) return@launch
                    dragging = false
                }
                if (reduceMotion) position.snapTo(target.toFloat()) else position.animateTo(
                    target.toFloat(),
                    spring(dampingRatio = .9f, stiffness = Spring.StiffnessMediumLow),
                    initialVelocity = velocity.coerceIn(-6f, 6f),
                )
            } finally {
                if (ticket == generation) settling = false
            }
        }
    }
}

/** Pointer timestamps, not the number of events, determine speed on different refresh rates. */
internal fun tabDragVelocity(deltaTabs: Float, elapsedMillis: Long): Float =
    if (elapsedMillis <= 0) 0f else (deltaTabs * 1000f / elapsedMillis).coerceIn(-10f, 10f)

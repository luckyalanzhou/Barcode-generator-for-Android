package com.luckyalanzhou.barcodegenerator.ui.component.glass

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.exp

/** 点击和拖动共用一个位置状态；图层读取动画值，不必重组 Tab 内容。 */
@Stable
internal class TabGlassMotionState(
    private val scope: CoroutineScope,
    initialIndex: Int,
    private val tabCount: Int,
) {
    private val position = Animatable(initialIndex.toFloat())
    private val dragVelocity = Animatable(0f, 5f)
    private val panelOffset = Animatable(0f, .5f)
    private val edgeRecovery = Animatable(0f, .001f)
    val impact = Animatable(0f)
    val contactSpread = Animatable(1f)
    var dragging by mutableStateOf(false)
        private set
    var settling by mutableStateOf(false)
        private set
    var settlingFromDrag by mutableStateOf(false)
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
    private var dragDisplayProgress by mutableFloatStateOf(initialIndex.toFloat())
    private var dragDisplayVelocity by mutableFloatStateOf(0f)
    private var dragStartX = 0f
    private var fingerPanelOffset by mutableFloatStateOf(0f)
    private var fingerEdgePull by mutableFloatStateOf(0f)
    private var generation = 0
    private var requestedTarget = initialIndex
    private var positionJob: Job? = null
    private var dragAnimationJob: Job? = null
    private var velocityJob: Job? = null
    private var impactJob: Job? = null
    private var contactJob: Job? = null
    private var panelRecoveryJob: Job? = null
    private var reduceMotion = false

    fun setReducedMotion(value: Boolean) {
        if (value == reduceMotion) return
        reduceMotion = value
        if (value) {
            impactJob?.cancel()
            contactJob?.cancel()
            scope.launch { impact.snapTo(0f); contactSpread.snapTo(1f) }
            dragAnimationJob?.cancel()
            dragAnimationJob = null
            if (dragging) {
                dragDisplayProgress = fingerProgress
                dragDisplayVelocity = 0f
            }
            if (!dragging) animatePosition(requestedTarget, fromFinger = false)
        } else if (dragging) {
            startDragAnimation(dragDisplayProgress)
        }
    }

    val progress: Float
        get() = (if (dragging) dragDisplayProgress else position.value)
            .coerceIn(0f, (tabCount - 1).toFloat())

    /** 手指目标位置；拖动中胶囊以阻尼弹簧追随，松手后继续弹到最近的 Tab。 */
    val targetProgress: Float
        get() = (if (dragging) fingerProgress else position.targetValue).coerceIn(0f, (tabCount - 1).toFloat())

/** 速度按整个 Tab 区间归一化，用于选中胶囊的拖动形变。 */
    val velocity: Float
        get() = if (reduceMotion) 0f else if (dragging) dragDisplayVelocity else dragVelocity.value

    val panelOffsetPx: Float
        get() = if (dragging) fingerPanelOffset else panelOffset.value

    val edgeStretch: Float
        get() = if (reduceMotion) 0f else abs(if (dragging) fingerEdgePull else edgeRecovery.value)

    /** 有符号边缘拉伸；用于让胶囊跟随手指越过首尾 Tab，并在松手后回弹。 */
    val edgePull: Float
        get() = if (reduceMotion) 0f else (if (dragging) fingerEdgePull else edgeRecovery.value)

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
        // Drag release owns the visual contact until its position spring settles.
        if (!settling && !dragging) pressed = false
    }

    fun beginDrag(point: Offset, timeMillis: Long) {
        val start = progress
        generation += 1
        positionJob?.cancel()
        dragAnimationJob?.cancel()
        fingerProgress = start
        dragDisplayProgress = start
        dragDisplayVelocity = 0f
        fingerEdgePull = 0f
        dragging = true
        settling = false
        settlingFromDrag = false
        dragStartX = point.x
        fingerPanelOffset = 0f
        panelRecoveryJob?.cancel()
        velocityJob?.cancel()
        scope.launch { dragVelocity.snapTo(0f) }
        if (!reduceMotion) startDragAnimation(start)
        updateTouch(point)
    }

    private fun startDragAnimation(start: Float) {
        dragAnimationJob?.cancel()
        dragAnimationJob = scope.launch {
            var previousFrame = 0L
            var displayPosition = start
            var displayVelocity = dragDisplayVelocity
            val angularFrequency = kotlin.math.sqrt(1000f)
            while (dragging && !reduceMotion) {
                val frameNanos = withFrameNanos { it }
                if (previousFrame == 0L) {
                    previousFrame = frameNanos
                    continue
                }
                val elapsedSeconds = ((frameNanos - previousFrame) / 1_000_000_000f).coerceIn(.001f, .032f)
                previousFrame = frameNanos
                val target = fingerProgress
                var displacement = displayPosition - target
                val helper = displayVelocity + angularFrequency * displacement
                val decay = exp(-angularFrequency * elapsedSeconds)
                displacement = decay * (displacement + helper * elapsedSeconds)
                displayVelocity = decay * (displayVelocity - helper * angularFrequency * elapsedSeconds)
                displayPosition = (target + displacement).coerceIn(0f, (tabCount - 1).toFloat())
                if (displayPosition == 0f || displayPosition == (tabCount - 1).toFloat()) {
                    displayVelocity = 0f
                }
                dragDisplayProgress = displayPosition
                dragDisplayVelocity = displayVelocity
            }
        }
    }

    fun drag(deltaTabs: Float, point: Offset, timeMillis: Long) {
        if (abs(deltaTabs) > .0005f) direction = if (deltaTabs > 0f) 1f else -1f
        val rawProgress = fingerProgress + deltaTabs
        fingerProgress = rawProgress.coerceIn(0f, (tabCount - 1).toFloat())
        if (reduceMotion) dragDisplayProgress = fingerProgress
        val overflow = rawProgress - fingerProgress
        fingerEdgePull = if (overflow == 0f) 0f else
            (fingerEdgePull + overflow).coerceIn(-1f, 1f)
        fingerPanelOffset = point.x - dragStartX
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

    fun release(index: Int) {
        dragAnimationJob?.cancel()
        dragAnimationJob = null
        pressed = !reduceMotion
        val releasedVelocity = dragDisplayVelocity
        velocityJob?.cancel()
        velocityJob = scope.launch {
            if (reduceMotion) {
                dragVelocity.snapTo(0f)
            } else {
                dragVelocity.snapTo(releasedVelocity)
                dragVelocity.animateTo(0f, spring(dampingRatio = .5f, stiffness = 300f, visibilityThreshold = .001f))
            }
        }
        panelRecoveryJob?.cancel()
        val releasedPull = fingerEdgePull
        val releasedPanelOffset = fingerPanelOffset
        panelRecoveryJob = scope.launch {
            launch {
                edgeRecovery.snapTo(if (reduceMotion) 0f else releasedPull)
                if (!reduceMotion) edgeRecovery.animateTo(0f, spring(dampingRatio = .85f, stiffness = 420f))
            }
            panelOffset.snapTo(if (reduceMotion) 0f else releasedPanelOffset)
            if (!reduceMotion) {
                panelOffset.animateTo(0f, spring(dampingRatio = 1f, stiffness = 300f, visibilityThreshold = .5f))
            }
        }
        animatePosition(index.coerceIn(0, tabCount - 1), fromFinger = true, animateContact = !reduceMotion)
    }

    fun animateTapTo(index: Int) {
        val target = index.coerceIn(0, tabCount - 1)
        animatePosition(target, fromFinger = false, animateContact = true)
    }

    fun pulse(amount: Float) {
        if (reduceMotion) return
        impactJob?.cancel()
        impactJob = scope.launch {
            impact.snapTo(amount.coerceIn(0f, .035f))
            impact.animateTo(0f, spring(dampingRatio = .82f, stiffness = 820f))
        }
    }

    private fun animatePosition(target: Int, fromFinger: Boolean, animateContact: Boolean = false) {
        val ticket = ++generation
        requestedTarget = target
        val dragReleasePosition = dragDisplayProgress
        val dragReleaseVelocity = dragDisplayVelocity
        positionJob?.cancel()
        settling = true
        settlingFromDrag = fromFinger
        if (animateContact) pressed = !reduceMotion
        positionJob = scope.launch {
            try {
                if (fromFinger) {
                    if (ticket != generation) return@launch
                    // Transfer from the exact finger position once, then animate only the release.
                    position.snapTo(dragReleasePosition)
                    dragging = false
                }
                if (reduceMotion) position.snapTo(target.toFloat()) else position.animateTo(
                    target.toFloat(),
                    spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = .001f),
                    initialVelocity = if (fromFinger && abs(target - dragReleasePosition) > .001f)
                        dragReleaseVelocity.coerceIn(-6f, 6f) else position.velocity,
                ) {
                    // Kyant 点按路径：接近落点时开始收拢，不等待位置弹簧完全结束。
                    if (animateContact && abs(value - target) < (tabCount - 1).coerceAtLeast(1) * .025f) {
                        pressed = false
                    }
                }
            } finally {
                if (ticket == generation) {
                    settling = false
                    if (animateContact) pressed = false
                }
            }
        }
    }
}

/** 通过指针事件的时间戳而非事件数量计算速度，以适配不同刷新率。 */
internal fun tabDragVelocity(deltaTabs: Float, elapsedMillis: Long): Float =
    if (elapsedMillis <= 0) 0f else (deltaTabs * 1000f / elapsedMillis).coerceIn(-10f, 10f)

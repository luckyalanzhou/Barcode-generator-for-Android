package com.luckyalanzhou.barcodegenerator

import android.content.Context
import android.graphics.PorterDuff
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/** 历史 Tab 的双层图标：外部回转环逆时针、中心指针顺时针，各旋转 45°。 */
internal class HistoryTabIconView(context: Context) : FrameLayout(context) {
    private val outer = ImageView(context).apply {
        tag = "historyTabIconOuter"
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(R.drawable.ic_tab_history_outer)
    }
    private val inner = ImageView(context).apply {
        tag = "historyTabIconInner"
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(R.drawable.ic_tab_history_inner)
    }
    private var selectedState: Boolean? = null
    private var scaleAnimations = emptyList<SpringAnimation>()
    private var pendingRestore: Runnable? = null

    init {
        tag = "historyTabIcon"
        clipChildren = false
        clipToPadding = false
        addView(outer, LayoutParams(-1, -1))
        addView(inner, LayoutParams(-1, -1))
    }

    fun setTint(color: Int) {
        outer.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        inner.setColorFilter(color, PorterDuff.Mode.SRC_IN)
    }

    fun setSelectedState(selected: Boolean) {
        if (selectedState == selected) return
        selectedState = selected
        cancelMotion()
        outer.rotation = 0f
        inner.rotation = 0f
        if (selected) playMotion()
    }

    private fun spring(target: Float, bouncy: Boolean): SpringForce = SpringForce(target).apply {
        dampingRatio = if (bouncy) SpringForce.DAMPING_RATIO_LOW_BOUNCY else SpringForce.DAMPING_RATIO_NO_BOUNCY
        stiffness = 320f
    }

    private fun playMotion() {
        scaleX = 1f
        scaleY = 1f
        val shrink = SpringAnimation(this, DynamicAnimation.SCALE_X).apply {
            spring = spring(0.5f, bouncy = false)
            addUpdateListener { _, value, _ ->
                val progress = ((1f - value) / 0.5f).coerceIn(0f, 1f)
                val angle = progress * 45f
                inner.rotation = angle
                outer.rotation = -angle
            }
            addEndListener { _, canceled, _, _ ->
                if (!canceled) {
                    val restore = Runnable { playRestoreMotion() }
                    pendingRestore = restore
                    postDelayed(restore, 45L)
                }
            }
        }
        val shrinkY = SpringAnimation(this, DynamicAnimation.SCALE_Y).apply {
            spring = spring(0.5f, bouncy = false)
        }
        scaleAnimations = listOf(shrink, shrinkY)
        shrink.start()
        shrinkY.start()
    }

    private fun playRestoreMotion() {
        pendingRestore = null
        val restore = SpringAnimation(this, DynamicAnimation.SCALE_X).apply {
            spring = spring(1f, bouncy = true)
            addUpdateListener { _, value, _ ->
                val progress = ((value - 0.5f) / 0.5f).coerceIn(0f, 1f)
                val angle = (1f - progress) * 45f
                inner.rotation = angle
                outer.rotation = -angle
            }
        }
        val restoreY = SpringAnimation(this, DynamicAnimation.SCALE_Y).apply {
            spring = spring(1f, bouncy = true)
        }
        scaleAnimations = listOf(restore, restoreY)
        restore.start()
        restoreY.start()
    }

    private fun cancelMotion() {
        pendingRestore?.let(::removeCallbacks)
        pendingRestore = null
        scaleAnimations.forEach(SpringAnimation::cancel)
        scaleAnimations = emptyList()
        scaleX = 1f
        scaleY = 1f
    }

    override fun onDetachedFromWindow() {
        cancelMotion()
        super.onDetachedFromWindow()
    }
}

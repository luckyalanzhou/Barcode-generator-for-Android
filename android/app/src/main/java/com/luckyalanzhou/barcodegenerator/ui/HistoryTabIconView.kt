package com.luckyalanzhou.barcodegenerator

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PorterDuff
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.PI
import kotlin.math.sin

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
    private var rotationAnimator: ValueAnimator? = null

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
        rotationAnimator?.cancel()
        outer.rotation = 0f
        inner.rotation = 0f
        if (selected) {
            rotationAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                // sin 曲线让起点、45°峰值和终点都自然减速，避免“转过去就硬弹回来”。
                duration = 560L
                interpolator = LinearInterpolator()
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    val angle = sin(progress * PI).toFloat() * 45f
                    inner.rotation = angle
                    outer.rotation = -angle
                }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        rotationAnimator?.cancel()
        rotationAnimator = null
        super.onDetachedFromWindow()
    }
}

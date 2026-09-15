package com.luckyalanzhou.barcodegenerator

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PorterDuff
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

/** 设置 Tab 的双层齿轮图标：中心顺时针、外环逆时针，选中时线性缩放并播放一次。 */
internal class SettingsTabIconView(context: Context) : FrameLayout(context) {
    private val outer = ImageView(context).apply {
        tag = "tabIconOuter"
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(R.drawable.ic_tab_settings_outer)
    }
    private val inner = ImageView(context).apply {
        tag = "tabIconInner"
        scaleType = ImageView.ScaleType.FIT_CENTER
        // 外齿轮中心有镂空，缩小中心齿轮后只在孔洞内显示，避免两层叠成实心图标。
        scaleX = 0.58f
        scaleY = 0.58f
        setImageResource(R.drawable.ic_tab_settings_inner)
    }
    private var selectedState: Boolean? = null
    private var motionAnimator: ValueAnimator? = null

    init {
        tag = "settingsTabIcon"
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
        if (selected) playLinearMotion()
    }

    private fun playLinearMotion() {
        motionAnimator = ValueAnimator.ofFloat(1f, 0.5f, 1f).apply {
            // 1 → 0.5 → 1 全程线性，无最小尺寸停留；两段各 180ms，避免瞬间缩放。
            duration = 360L
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val value = (animator.animatedValue as Float).coerceIn(0.5f, 1f)
                scaleX = value
                scaleY = value
                val angle = if (animator.animatedFraction <= 0.5f) {
                    ((1f - value) / 0.5f).coerceIn(0f, 1f) * 45f
                } else {
                    ((value - 0.5f) / 0.5f).coerceIn(0f, 1f).let { (1f - it) * 45f }
                }
                inner.rotation = angle
                outer.rotation = -angle
            }
            start()
        }
    }

    private fun cancelMotion() {
        motionAnimator?.cancel()
        motionAnimator = null
        scaleX = 1f
        scaleY = 1f
        outer.rotation = 0f
        inner.rotation = 0f
    }

    override fun onDetachedFromWindow() {
        cancelMotion()
        super.onDetachedFromWindow()
    }
}

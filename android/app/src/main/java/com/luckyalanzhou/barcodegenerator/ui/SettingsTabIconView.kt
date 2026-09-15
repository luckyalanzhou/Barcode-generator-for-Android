package com.luckyalanzhou.barcodegenerator

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PorterDuff
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.PI
import kotlin.math.sin

/** 设置 Tab 的双层齿轮图标：中心顺时针、外环逆时针，仅在选中时播放一次。 */
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
    private var rotationAnimator: ValueAnimator? = null

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
        rotationAnimator?.cancel()
        outer.rotation = 0f
        inner.rotation = 0f
        if (selected) {
            // 与历史图标完全一致：同一条曲线完成 0° → 45° → 0°。
            rotationAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
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

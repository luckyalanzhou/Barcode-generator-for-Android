package com.luckyalanzhou.barcodegenerator

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.PorterDuff
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

/** 设置 Tab 的双层齿轮图标：中心顺时针、外环逆时针，仅在选中时播放一次。 */
internal class SettingsTabIconView(context: Context) : FrameLayout(context) {
    private val outer = ImageView(context).apply {
        tag = "tabIconOuter"
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setImageResource(R.drawable.ic_tab_settings_outer)
    }
    private val inner = ImageView(context).apply {
        tag = "tabIconInner"
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setImageResource(R.drawable.ic_tab_settings_inner)
    }
    private var selectedState: Boolean? = null
    private var rotationAnimator: AnimatorSet? = null

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
            // 正值为顺时针，负值为逆时针；每次只转 45°，保持轻量的设置图标反馈。
            rotationAnimator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(inner, View.ROTATION, 0f, 45f),
                    ObjectAnimator.ofFloat(outer, View.ROTATION, 0f, -45f)
                )
                duration = 300L
                interpolator = DecelerateInterpolator(1.25f)
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

package com.luckyalanzhou.barcodegenerator

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.PorterDuff
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView

/** 历史 Tab 的双层图标：外部回转环逆时针、中心指针顺时针，各旋转 45°。 */
internal class HistoryTabIconView(context: Context) : FrameLayout(context) {
    private val outer = ImageView(context).apply {
        tag = "historyTabIconOuter"
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setImageResource(R.drawable.ic_tab_history_outer)
    }
    private val inner = ImageView(context).apply {
        tag = "historyTabIconInner"
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setImageResource(R.drawable.ic_tab_history_inner)
    }
    private var selectedState: Boolean? = null
    private var rotationAnimator: AnimatorSet? = null

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

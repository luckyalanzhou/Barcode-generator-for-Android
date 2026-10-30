package com.luckyalanzhou.barcodegenerator.ui.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

/** 降低拖动增益可减缓来源控件的移动，同时避免为每次指针更新都排队启动动画。 */
internal object TabMenuSourceMotion {
    const val dragRangeDp = 96f
    const val horizontalLimitDp = 20f
    const val verticalLimitDp = 22f
    const val returnStiffness = 300f
    const val returnDamping = .95f
}

internal fun tabMenuIconMotion(motion: Offset): Offset {
    val ratio = TabMenuSourceMotion.dragRangeDp / 64f
    fun resist(value: Float): Float = value / (ratio + (1f - ratio) * abs(value))
    return Offset(resist(motion.x), resist(motion.y))
}

// HIG：上下文菜单标题只有在能说明操作对象或操作结果时才有价值。
internal fun menuShowsTitle(tabAnchor: Boolean, title: String): Boolean =
    title.isNotBlank() && (!tabAnchor || title != "操作")

/** 使用同一个展开进度驱动几何、光线和厚度，不让各部分独立淡入淡出。 */
internal data class MenuGlassReveal(val scale: Float, val alpha: Float, val thickness: Float, val shadow: Float)

internal fun menuGlassReveal(progress: Float, reduceMotion: Boolean): MenuGlassReveal {
    val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    if (reduceMotion) return MenuGlassReveal(1f, p, 1f, 1f)
    // 两类菜单都整体展开；仅允许很小的弹簧超调，不通过裁切隐藏菜单行。
    val elastic = if (progress.isFinite()) progress.coerceIn(0f, 1.08f) else 0f
    return MenuGlassReveal(.94f + .06f * elastic,
        p, p * p * (3f - 2f * p), .35f + .65f * p)
}

/** 将位移归一化并限制范围；菜单面板作为整体移动，不单独移动文字或图标。 */
internal fun menuPanelMotion(point: Offset?, panel: Rect): Offset {
    if (point == null || panel.width <= 0f || panel.height <= 0f) return Offset.Zero
    return Offset(
        ((point.x - panel.center.x) / (panel.width / 2f)).coerceIn(-1f, 1f),
        ((point.y - panel.center.y) / (panel.height / 2f)).coerceIn(-1f, 1f),
    )
}

/** 手指保持在来源图标上；位移从长按起点开始计算。 */
internal fun menuAnchorMotion(point: Offset?, origin: Offset, rangePx: Float): Offset {
    if (point == null || rangePx <= 0f) return Offset.Zero
    val delta = point - origin
    // 橡皮筋阻力让较大拖动仍有响应，不会突然撞上位移上限。
    return Offset(delta.x / (rangePx + abs(delta.x)),
        delta.y / (rangePx + abs(delta.y)))
}

internal fun menuDragScale(motion: Offset): Float =
    1f - .03f * maxOf(abs(motion.x), abs(motion.y)).coerceIn(0f, 1f)

internal data class TabMenuDragTransform(val scale: Float, val translation: Offset)

/** 弹出围绕来源锚点，拖动围绕菜单自身下角；合成为一次图层变换，避免修改 pivot 时跳动。 */
internal fun tabMenuDragTransform(
    motion: Offset, presentationScale: Float, pivotX: Float, pivotY: Float,
    panel: IntSize, rightSide: Boolean,
): TabMenuDragTransform {
    fun finiteMagnitude(value: Float) = if (value.isFinite()) abs(value).coerceIn(0f, 1f) else 0f
    // 64dp 拖动约缩小 5%，极限 10%；图标仍保持原来的小范围阻尼位移。
    val scale = 1f - .10f * maxOf(finiteMagnitude(motion.x), finiteMagnitude(motion.y))
    if (scale == 1f) return TabMenuDragTransform(1f, Offset.Zero)
    val compensation = presentationScale * (1f - scale)
    return TabMenuDragTransform(scale, Offset(
        compensation * ((if (rightSide) 1f else 0f) - pivotX) * panel.width,
        compensation * (1f - pivotY) * panel.height,
    ))
}

/** 手指朝菜单方向滑动时选中菜单项，而不是把菜单推开。 */
internal fun menuSourceInteractionMotion(
    point: Offset?, origin: Offset, rangePx: Float, menuAbove: Boolean,
): Offset {
    if (point == null) return Offset.Zero
    val delta = point - origin
    val towardMenu = if (menuAbove) delta.y < 0f else delta.y > 0f
    if (towardMenu && abs(delta.y) >= abs(delta.x)) return Offset.Zero
    return menuAnchorMotion(point, origin, rangePx)
}

/** 拖动进行中不要订阅独立播放的回弹状态。 */
internal inline fun menuMotionForDrawing(
    dragMotion: Offset?, reduceMotion: Boolean, returnMotion: () -> Offset,
): Offset = if (reduceMotion) Offset.Zero else dragMotion ?: returnMotion()

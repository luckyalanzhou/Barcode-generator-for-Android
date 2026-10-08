package com.luckyalanzhou.barcodegenerator.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 菜单只共享视觉进度，业务操作仍由宿主在退场完成后执行。 */
internal class ContextMenuMotion {
    internal val revealAnimation = Animatable(0f)
    internal val actionExitAnimation = Animatable(0f)
    internal val sourceScaleAnimation = Animatable(.97f)
    val reveal: State<Float> = revealAnimation.asState()
    val actionExit: State<Float> = actionExitAnimation.asState()
    val sourceScale: State<Float> = sourceScaleAnimation.asState()
}

/**
 * 一个可取消任务协调来源弹起、背景和面板；重新打开菜单会取消旧退场。
 * 点击空白向来源收回；选中操作先确认，再整体缩小淡出；减少动态效果时仅短暂淡入淡出。
 */
@Composable
internal fun rememberContextMenuMotion(
    identity: Any?, open: Boolean, ready: Boolean, actionClosing: Boolean,
    reduceMotion: Boolean, onClosed: (Any) -> Unit,
): ContextMenuMotion {
    val motion = remember { ContextMenuMotion() }
    val closed by rememberUpdatedState(onClosed)
    LaunchedEffect(identity, open, ready, reduceMotion) {
        val showing = identity ?: return@LaunchedEffect
        if (open && ready) {
            motion.actionExitAnimation.snapTo(0f)
            coroutineScope {
                launch {
                    motion.sourceScaleAnimation.snapTo(if (reduceMotion) 1f else .97f)
                    motion.sourceScaleAnimation.animateTo(1f, if (reduceMotion) tween(90)
                        else spring(dampingRatio = .78f, stiffness = 550f))
                }
                motion.revealAnimation.animateTo(1f, if (reduceMotion) tween(90)
                    else spring(dampingRatio = .88f, stiffness = 650f))
            }
        } else if (!open) {
            if (actionClosing && !reduceMotion) delay(70)
            coroutineScope {
                if (actionClosing) launch {
                    motion.actionExitAnimation.animateTo(1f, tween(if (reduceMotion) 90 else 170))
                }
                motion.revealAnimation.animateTo(0f,
                    tween(if (reduceMotion) 90 else if (actionClosing) 170 else 200,
                        easing = FastOutSlowInEasing))
            }
            closed(showing)
        } else {
            motion.revealAnimation.snapTo(0f)
            motion.actionExitAnimation.snapTo(0f)
        }
    }
    return motion
}

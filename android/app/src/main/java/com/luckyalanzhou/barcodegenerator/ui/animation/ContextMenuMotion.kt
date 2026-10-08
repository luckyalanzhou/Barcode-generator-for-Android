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
    internal val panelAnimation = Animatable(0f)
    internal val opacityAnimation = Animatable(0f)
    internal val actionExitAnimation = Animatable(0f)
    internal val sourceAnimation = Animatable(0f)
    val reveal: State<Float> = revealAnimation.asState()
    val actionExit: State<Float> = actionExitAnimation.asState()
    val panel: State<Float> = panelAnimation.asState()
    val opacity: State<Float> = opacityAnimation.asState()
    val source: State<Float> = sourceAnimation.asState()
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
                    motion.sourceAnimation.animateTo(1f, if (reduceMotion) tween(90)
                        else spring(dampingRatio = .72f, stiffness = 460f))
                }
                launch {
                    motion.panelAnimation.animateTo(1f, if (reduceMotion) tween(90)
                        else spring(dampingRatio = .74f, stiffness = 420f))
                }
                launch {
                    if (!reduceMotion) delay(25)
                    motion.opacityAnimation.animateTo(1f, tween(if (reduceMotion) 90 else 170))
                }
                // 背景只平滑变化，不随面板弹簧忽清忽糊。
                motion.revealAnimation.animateTo(1f, if (reduceMotion) tween(90)
                    else tween(240, easing = FastOutSlowInEasing))
            }
        } else if (!open) {
            if (actionClosing && !reduceMotion) delay(70)
            coroutineScope {
                if (actionClosing) launch {
                    motion.actionExitAnimation.animateTo(1f, tween(if (reduceMotion) 90 else 170))
                }
                if (!actionClosing) {
                    launch { motion.panelAnimation.animateTo(0f, tween(if (reduceMotion) 90 else 200)) }
                    launch { motion.sourceAnimation.animateTo(0f, tween(if (reduceMotion) 90 else 200)) }
                }
                launch { motion.opacityAnimation.animateTo(0f, tween(if (reduceMotion) 90 else 180)) }
                motion.revealAnimation.animateTo(0f,
                    tween(if (reduceMotion) 90 else if (actionClosing) 170 else 200,
                        easing = FastOutSlowInEasing))
            }
            closed(showing)
        } else {
            motion.revealAnimation.snapTo(0f)
            motion.panelAnimation.snapTo(0f)
            motion.opacityAnimation.snapTo(0f)
            motion.sourceAnimation.snapTo(0f)
            motion.actionExitAnimation.snapTo(0f)
        }
    }
    return motion
}

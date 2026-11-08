package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

/** 只在用户触发展开或折叠时动画行变化；普通列表滚动保持即时呈现。 */
internal fun shouldAnimateFavoriteRowChanges(rowAnimationActive: Boolean, reduceMotion: Boolean): Boolean =
    rowAnimationActive && !reduceMotion

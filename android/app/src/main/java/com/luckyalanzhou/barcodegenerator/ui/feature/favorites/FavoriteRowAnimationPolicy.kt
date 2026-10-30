package com.luckyalanzhou.barcodegenerator.ui.feature.favorites

/** 收藏列表只在首次恢复完成后播放行过渡；系统减少动态效果时保持即时更新。 */
internal fun shouldAnimateFavoriteRowChanges(listPositionRestored: Boolean, reduceMotion: Boolean): Boolean =
    listPositionRestored && !reduceMotion

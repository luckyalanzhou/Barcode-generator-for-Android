package com.luckyalanzhou.barcodegenerator.ui.app

/** 只有再次点击当前已显示页面的 Tab，才触发该页面的次级操作。 */
internal fun isTabReselection(current: AppRoute, target: AppRoute, fromSwipe: Boolean): Boolean =
    !fromSwipe && current == target && target.mainTabIndex != null

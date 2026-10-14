package com.luckyalanzhou.barcodegenerator.ui.app

/** Secondary actions only belong to a tap on the page already being displayed. */
internal fun isTabReselection(current: AppRoute, target: AppRoute, fromSwipe: Boolean): Boolean =
    !fromSwipe && current == target && target.mainTabIndex != null

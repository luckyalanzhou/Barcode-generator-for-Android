package com.luckyalanzhou.barcodegenerator

/** 找出系统要求先解释用途的权限，或已拒绝且系统不再展示请求框的权限。 */
internal fun permissionRequiringRationale(
    permissions: Array<String>,
    shouldShowRationale: (String) -> Boolean,
): String? = permissions.firstOrNull(shouldShowRationale)

internal fun findPermanentlyDeniedPermission(
    result: Map<String, Boolean>,
    shouldShowRationale: (String) -> Boolean,
): String? = result.entries
    .firstOrNull { (permission, granted) -> !granted && !shouldShowRationale(permission) }
    ?.key

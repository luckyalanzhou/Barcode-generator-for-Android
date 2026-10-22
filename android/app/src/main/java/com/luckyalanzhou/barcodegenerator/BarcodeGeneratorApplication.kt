package com.luckyalanzhou.barcodegenerator

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

/** Hilt 应用根容器；业务功能和现有 Activity 生命周期保持不变。 */
@HiltAndroidApp
class BarcodeGeneratorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        releaseObsoleteDocumentUriGrants()
    }

    /** LAN 附件 URI 只服务于内存中的一次上传；清除旧版本留下的跨重启授权。 */
    private fun releaseObsoleteDocumentUriGrants() {
        val migrationPreferences = getSharedPreferences(URI_GRANT_MIGRATION_PREFERENCES, MODE_PRIVATE)
        if (migrationPreferences.getBoolean(URI_GRANT_MIGRATION_COMPLETED, false)) return

        val grants = captureRecoverableDocumentUriGrantOperation {
            contentResolver.persistedUriPermissions.toList()
        }
            .getOrElse { error ->
                Log.w(TAG, "Unable to inspect persisted document access grants", error)
                return
            }
        val releaseFailures = releasePersistedDocumentUriGrants(
            grants.map { PersistedDocumentUriGrant(it.uri, it.isReadPermission, it.isWritePermission) },
            contentResolver::releasePersistableUriPermission,
        )
        if (releaseFailures > 0) {
            Log.w(TAG, "Unable to release $releaseFailures obsolete document access grants")
        } else {
            // 这是一次性的旧版本迁移；后续版本新增的持久 URI 授权不应被每次启动清除。
            migrationPreferences.edit().putBoolean(URI_GRANT_MIGRATION_COMPLETED, true).apply()
        }
    }

    private companion object {
        const val TAG = "BarcodeGenerator"
        const val URI_GRANT_MIGRATION_PREFERENCES = "uri_grant_migrations"
        const val URI_GRANT_MIGRATION_COMPLETED = "lan_share_temporary_uri_access_v1"
    }
}

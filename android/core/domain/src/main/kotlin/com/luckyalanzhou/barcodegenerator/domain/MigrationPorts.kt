package com.luckyalanzhou.barcodegenerator.domain

/** 兼容性迁移接口；实现保留在 Data 层。 */
interface BarcodeDataMigration {
    suspend fun migrateIfNeeded()
}

interface SettingsMigration {
    suspend fun migrateIfNeeded(): StyleSettings?
}

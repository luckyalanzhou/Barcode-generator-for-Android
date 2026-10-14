package com.luckyalanzhou.barcodegenerator.domain

/** Compatibility migration ports; implementations remain in the Data layer. */
interface BarcodeDataMigration {
    suspend fun migrateIfNeeded()
}

interface SettingsMigration {
    suspend fun migrateIfNeeded(): StyleSettings?
}

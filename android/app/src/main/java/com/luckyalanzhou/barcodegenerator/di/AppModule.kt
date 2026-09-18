package com.luckyalanzhou.barcodegenerator.di

import android.content.Context
import com.luckyalanzhou.barcodegenerator.BarcodeDao
import com.luckyalanzhou.barcodegenerator.BarcodeDatabase
import com.luckyalanzhou.barcodegenerator.FavoritesBackupUseCase
import com.luckyalanzhou.barcodegenerator.GenerateBarcodesUseCase
import com.luckyalanzhou.barcodegenerator.LanShareManager
import com.luckyalanzhou.barcodegenerator.LocalBarcodeFileStore
import com.luckyalanzhou.barcodegenerator.RoomBarcodeRepository
import com.luckyalanzhou.barcodegenerator.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.SettingsStore
import com.luckyalanzhou.barcodegenerator.UpdateDownloadService
import com.luckyalanzhou.barcodegenerator.UpdateCheckService
import com.luckyalanzhou.barcodegenerator.OcrTextService
import com.luckyalanzhou.barcodegenerator.BarcodeDecodeService
import com.luckyalanzhou.barcodegenerator.LegacySettingsMigrator
import com.luckyalanzhou.barcodegenerator.LegacyBarcodeDataMigrator
import com.luckyalanzhou.barcodegenerator.ApkUpdateValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 第一阶段依赖图：只接管原来由 MainActivity 手动创建的基础设施。 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    internal fun provideBarcodeDatabase(@ApplicationContext context: Context): BarcodeDatabase =
        BarcodeDatabase.create(context)

    @Provides
    internal fun provideBarcodeDao(database: BarcodeDatabase): BarcodeDao = database.barcodeDao()

    @Provides
    @Singleton
    internal fun provideBarcodeRepository(database: BarcodeDatabase): BarcodeRepository =
        RoomBarcodeRepository(database)

    @Provides
    internal fun provideGenerateBarcodesUseCase(): GenerateBarcodesUseCase = GenerateBarcodesUseCase()

    @Provides
    internal fun provideFavoritesBackupUseCase(repository: BarcodeRepository): FavoritesBackupUseCase =
        FavoritesBackupUseCase(repository)

    @Provides
    @Singleton
    internal fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStore(context)

    @Provides
    @Singleton
    internal fun provideLocalBarcodeFileStore(@ApplicationContext context: Context): LocalBarcodeFileStore =
        LocalBarcodeFileStore(context)

    @Provides
    @Singleton
    internal fun provideUpdateDownloadService(@ApplicationContext context: Context): UpdateDownloadService =
        UpdateDownloadService(context)

    @Provides
    @Singleton
    internal fun provideUpdateCheckService(): UpdateCheckService = UpdateCheckService()

    @Provides
    @Singleton
    internal fun provideApkUpdateValidator(@ApplicationContext context: Context): ApkUpdateValidator =
        ApkUpdateValidator(context)

    @Provides
    @Singleton
    internal fun provideOcrTextService(): OcrTextService = OcrTextService()

    @Provides
    @Singleton
    internal fun provideBarcodeDecodeService(): BarcodeDecodeService = BarcodeDecodeService()

    @Provides
    internal fun provideLegacySettingsMigrator(
        @ApplicationContext context: Context,
        settingsStore: SettingsStore,
    ): LegacySettingsMigrator = LegacySettingsMigrator(context, settingsStore)

    @Provides
    @Singleton
    internal fun provideLegacyBarcodeDataMigrator(
        @ApplicationContext context: Context,
        repository: BarcodeRepository,
    ): LegacyBarcodeDataMigrator = LegacyBarcodeDataMigrator(context, repository)

    @Provides
    @Singleton
    internal fun provideLanShareManager(@ApplicationContext context: Context): LanShareManager =
        LanShareManager(context)
}

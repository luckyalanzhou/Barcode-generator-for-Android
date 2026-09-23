package com.luckyalanzhou.barcodegenerator.di

import android.content.Context
import com.luckyalanzhou.barcodegenerator.data.network.server.LanShareManager
import com.luckyalanzhou.barcodegenerator.data.BarcodeDatabase
import com.luckyalanzhou.barcodegenerator.data.BarcodeDao
import com.luckyalanzhou.barcodegenerator.data.FavoritesBackupUseCase
import com.luckyalanzhou.barcodegenerator.data.LegacyBarcodeDataMigrator
import com.luckyalanzhou.barcodegenerator.data.LegacySettingsMigrator
import com.luckyalanzhou.barcodegenerator.data.LocalBarcodeFileStore
import com.luckyalanzhou.barcodegenerator.data.RoomBarcodeRepository
import com.luckyalanzhou.barcodegenerator.data.SettingsStore
import com.luckyalanzhou.barcodegenerator.data.platform.AndroidApkDownloadGateway
import com.luckyalanzhou.barcodegenerator.data.platform.AndroidApkValidationGateway
import com.luckyalanzhou.barcodegenerator.data.platform.AndroidUpdateCatalogGateway
import com.luckyalanzhou.barcodegenerator.data.platform.MlKitOcrTextGateway
import com.luckyalanzhou.barcodegenerator.data.platform.ZxingBarcodeDecodeGateway
import com.luckyalanzhou.barcodegenerator.domain.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.domain.AppLogger
import com.luckyalanzhou.barcodegenerator.domain.ApkDownloadGateway
import com.luckyalanzhou.barcodegenerator.domain.ApkValidationGateway
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDecodeGateway
import com.luckyalanzhou.barcodegenerator.domain.FavoritesBackupRepository
import com.luckyalanzhou.barcodegenerator.domain.GenerateBarcodesUseCase
import com.luckyalanzhou.barcodegenerator.domain.LanShareGateway
import com.luckyalanzhou.barcodegenerator.domain.BarcodeDataMigration
import com.luckyalanzhou.barcodegenerator.domain.SettingsMigration
import com.luckyalanzhou.barcodegenerator.domain.SettingsRepository
import com.luckyalanzhou.barcodegenerator.domain.OcrTextGateway
import com.luckyalanzhou.barcodegenerator.domain.UpdateCatalogGateway
import com.luckyalanzhou.barcodegenerator.ui.support.logging.DebugLog
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
    internal fun provideFavoritesBackupRepository(useCase: FavoritesBackupUseCase): FavoritesBackupRepository = useCase

    @Provides
    @Singleton
    internal fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
        SettingsStore(context)

    @Provides
    @Singleton
    internal fun provideSettingsRepository(settingsStore: SettingsStore): SettingsRepository = settingsStore

    @Provides
    @Singleton
    internal fun provideLocalBarcodeFileStore(@ApplicationContext context: Context): LocalBarcodeFileStore =
        LocalBarcodeFileStore(context)

    @Provides
    @Singleton
    internal fun provideUpdateDownloadGateway(@ApplicationContext context: Context, logger: AppLogger): ApkDownloadGateway =
        AndroidApkDownloadGateway(context, logger, "BarcodeGenerator/${com.luckyalanzhou.barcodegenerator.BuildConfig.VERSION_NAME}")

    @Provides
    @Singleton
    internal fun provideUpdateCatalogGateway(logger: AppLogger): UpdateCatalogGateway = AndroidUpdateCatalogGateway(
        logger = logger,
        updateTagPrefix = com.luckyalanzhou.barcodegenerator.BuildConfig.UPDATE_TAG_PREFIX,
        apkFilePrefix = com.luckyalanzhou.barcodegenerator.BuildConfig.APK_FILE_PREFIX,
        currentVersionName = com.luckyalanzhou.barcodegenerator.BuildConfig.VERSION_NAME,
        currentVersionCode = com.luckyalanzhou.barcodegenerator.BuildConfig.VERSION_CODE.toLong(),
    )

    @Provides
    @Singleton
    internal fun provideApkValidationGateway(@ApplicationContext context: Context): ApkValidationGateway =
        AndroidApkValidationGateway(context, com.luckyalanzhou.barcodegenerator.BuildConfig.VERSION_CODE.toLong())

    @Provides
    @Singleton
    internal fun provideOcrTextGateway(): OcrTextGateway = MlKitOcrTextGateway()

    @Provides
    @Singleton
    internal fun provideBarcodeDecodeGateway(): BarcodeDecodeGateway = ZxingBarcodeDecodeGateway()

    @Provides
    internal fun provideLegacySettingsMigrator(
        @ApplicationContext context: Context,
        settingsStore: SettingsStore,
    ): SettingsMigration = LegacySettingsMigrator(context, settingsStore)

    @Provides
    @Singleton
    internal fun provideLegacyBarcodeDataMigrator(
        @ApplicationContext context: Context,
        repository: BarcodeRepository,
    ): BarcodeDataMigration = LegacyBarcodeDataMigrator(context, repository)

    @Provides
    @Singleton
    internal fun provideLanShareManager(@ApplicationContext context: Context): LanShareGateway =
        LanShareManager(context, provideAppLogger())

    @Provides
    @Singleton
    internal fun provideAppLogger(): AppLogger = AppLogger { tag, message, error ->
        DebugLog.record(tag, message, error)
    }
}

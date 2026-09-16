package com.luckyalanzhou.barcodegenerator.di

import android.content.Context
import com.luckyalanzhou.barcodegenerator.BarcodeDao
import com.luckyalanzhou.barcodegenerator.BarcodeDatabase
import com.luckyalanzhou.barcodegenerator.BarcodeRepository
import com.luckyalanzhou.barcodegenerator.FavoritesBackupUseCase
import com.luckyalanzhou.barcodegenerator.GenerateBarcodesUseCase
import com.luckyalanzhou.barcodegenerator.LanShareManager
import com.luckyalanzhou.barcodegenerator.SettingsStore
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
        BarcodeRepository(database)

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
    internal fun provideLanShareManager(@ApplicationContext context: Context): LanShareManager =
        LanShareManager(context)
}

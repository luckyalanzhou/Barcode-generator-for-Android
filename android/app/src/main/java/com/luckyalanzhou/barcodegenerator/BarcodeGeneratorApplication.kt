package com.luckyalanzhou.barcodegenerator

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt 应用根容器；业务功能和现有 Activity 生命周期保持不变。 */
@HiltAndroidApp
class BarcodeGeneratorApplication : Application()

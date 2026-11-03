package com.luckyalanzhou.barcodegenerator.di

import javax.inject.Qualifier

/** 提供进程级协程作用域，供需要跨 ViewModel 清理的异步工作使用。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

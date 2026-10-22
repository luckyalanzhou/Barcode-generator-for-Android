package com.luckyalanzhou.barcodegenerator.domain

import kotlinx.coroutines.Job

/** 更新失败状态只保存通用摘要，平台异常详情仅用于当前界面提示。 */
const val UPDATE_FAILURE_SUMMARY = "安装失败"

/** 设置持久化边界；Domain/UI 不需要知道 DataStore 的具体实现。 */
interface SettingsRepository {
    suspend fun load()
    fun loadStyle(): StyleSettings
    fun saveStyle(style: StyleSettings): Job
    fun getOcrConfusionReplacementMask(): Int
    fun setOcrConfusionReplacementMask(mask: Int): Job
    fun setUpdateError(error: String): Job
}

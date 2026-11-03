package com.luckyalanzhou.barcodegenerator.domain

/** 领域层条码项；不依赖 Android、Compose 或 Activity。 */
data class CodeItem(
    val id: Long,
    var text: String,
    var format: String,
    var createdAt: Long = System.currentTimeMillis(),
    var favorite: Boolean = false,
    var folder: String = "默认",
    var inHistory: Boolean = true,
    /** 新生成时标识所属批次；空值表示旧数据，历史页继续按时间兼容分组。 */
    val historyBatchId: String = "",
)

/** 领域层收藏分组；UI 和持久化层通过这个模型交换数据。 */
data class FavoriteGroup(
    val id: Long,
    var folder: String,
    var name: String,
    val savedAt: Long,
    var itemIds: MutableList<Long>
)

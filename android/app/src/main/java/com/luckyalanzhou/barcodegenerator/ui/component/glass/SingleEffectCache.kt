package com.luckyalanzhou.barcodegenerator.ui.component.glass

/** 同时只保留一个不可变效果，不缓存无上限增长的动画坐标。 */
internal class SingleEffectCache<K, V : Any> {
    private var previous: K? = null
    private var cached: V? = null
    fun get(key: K, create: () -> V): V {
        if (key == previous) cached?.let { return it }
        return create().also { previous = key; cached = it }
    }
}

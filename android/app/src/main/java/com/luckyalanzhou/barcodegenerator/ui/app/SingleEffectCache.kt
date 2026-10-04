package com.luckyalanzhou.barcodegenerator.ui.app

/** One immutable effect at a time, no unbounded cache of animated coordinates. */
internal class SingleEffectCache<K, V : Any> {
    private var previous: K? = null
    private var cached: V? = null
    fun get(key: K, create: () -> V): V {
        if (key == previous) cached?.let { return it }
        return create().also { previous = key; cached = it }
    }
}

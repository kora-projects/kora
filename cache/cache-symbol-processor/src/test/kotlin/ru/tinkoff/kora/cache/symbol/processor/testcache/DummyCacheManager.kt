package ru.tinkoff.kora.cache.symbol.processor.testcache

import ru.tinkoff.kora.cache.Cache
import ru.tinkoff.kora.cache.CacheManager
import ru.tinkoff.kora.cache.LoadableCache

class DummyCacheManager<K, V> : CacheManager<K, V> {

    private val cacheMap = HashMap<String, DummyCache<K, V>>()

    override fun getCache(name: String): DummyCache<K, V> {
        return cacheMap.computeIfAbsent(name) { DummyCache(name) }
    }

    fun reset() {
        cacheMap.values.forEach { c -> c.reset() }
    }
}

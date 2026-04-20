package ru.tinkoff.kora.cache.symbol.processor.testcache

import ru.tinkoff.kora.cache.caffeine.CaffeineCache

interface DummyCacheInherit : CaffeineCache<String, String> {

    fun getValue(arg1: String): String? {
        return get(arg1)
    }
}

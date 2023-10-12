package ru.tinkoff.kora.cache.symbol.processor.testdata

import ru.tinkoff.kora.cache.annotation.CacheInvalidate
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache11
import java.math.BigDecimal

open class CacheableSyncOne {
    var value = "1"

    @Cacheable(value = DummyCache11::class)
    open fun getValue(arg1: String): String {
        return value
    }

    @CachePut(value = DummyCache11::class, parameters = ["arg1"])
    open fun putValue(arg2: BigDecimal?, arg3: String?, arg1: String): String {
        return value
    }

    @CacheInvalidate(value = DummyCache11::class)
    open fun evictValue(arg1: String) {
    }

    @CacheInvalidate(value = DummyCache11::class, invalidateAll = true)
    open fun evictAll() {
    }
}

package ru.tinkoff.kora.cache.symbol.processor.testdata.suspended

import ru.tinkoff.kora.cache.annotation.CacheInvalidate
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache1
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache12
import java.math.BigDecimal

open class CacheableSuspendOneMany {
    var value = "1"

    @Cacheable(value = DummyCache1::class)
    @Cacheable(value = DummyCache12::class)
    open suspend fun getValue(arg1: String): String {
        return value
    }

    @CachePut(value = DummyCache1::class, parameters = ["arg1"])
    @CachePut(value = DummyCache12::class, parameters = ["arg1"])
    open suspend fun putValue(arg2: BigDecimal?, arg3: String?, arg1: String): String {
        return value
    }

    @CacheInvalidate(value = DummyCache1::class)
    @CacheInvalidate(value = DummyCache12::class)
    open suspend fun evictValue(arg1: String) {
    }

    @CacheInvalidate(value = DummyCache1::class, invalidateAll = true)
    @CacheInvalidate(value = DummyCache12::class, invalidateAll = true)
    open suspend fun evictAll() {
    }
}

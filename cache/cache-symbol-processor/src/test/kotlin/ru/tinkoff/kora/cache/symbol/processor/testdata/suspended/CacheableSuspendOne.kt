package ru.tinkoff.kora.cache.symbol.processor.testdata.suspended

import kotlinx.coroutines.delay
import ru.tinkoff.kora.cache.annotation.CacheInvalidate
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache1
import java.math.BigDecimal

open class CacheableSuspendOne {
    var value = "1"

    @Cacheable(value = DummyCache1::class)
    open suspend fun getValue(arg1: String?): String {
        delay(10)
        return value
    }

    @CachePut(value = DummyCache1::class, parameters = ["arg1"])
    open suspend fun putValue(arg2: BigDecimal?, arg3: String?, arg1: String?): String {
        delay(10)
        return value
    }

    @CacheInvalidate(value = DummyCache1::class)
    open suspend fun evictValue(arg1: String?) {
        delay(10)
    }

    @CacheInvalidate(value = DummyCache1::class, invalidateAll = true)
    open suspend fun evictAll() {
        delay(10)
    }
}

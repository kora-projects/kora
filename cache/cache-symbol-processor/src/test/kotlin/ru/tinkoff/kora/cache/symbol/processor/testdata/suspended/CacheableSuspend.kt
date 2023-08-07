package ru.tinkoff.kora.cache.symbol.processor.testdata.suspended

import kotlinx.coroutines.delay
import ru.tinkoff.kora.cache.annotation.CacheInvalidate
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache2
import java.math.BigDecimal

open class CacheableSuspend {
    var value = "1"

    @Cacheable(value = DummyCache2::class)
    open suspend fun getValue(arg1: String?, arg2: BigDecimal?): String {
        delay(10)
        return value
    }

    @CachePut(value = DummyCache2::class, parameters = ["arg1", "arg2"])
    open suspend fun putValue(arg2: BigDecimal?, arg3: String?, arg1: String?): String {
        delay(10)
        return value
    }

    @CacheInvalidate(value = DummyCache2::class)
    open suspend fun evictValue(arg1: String?, arg2: BigDecimal?) {
        delay(10)
    }

    @CacheInvalidate(value = DummyCache2::class, invalidateAll = true)
    open suspend fun evictAll() {
        delay(10)
    }
}

package io.koraframework.cache.symbol.processor.testdata

import io.koraframework.cache.annotation.CacheInvalidate
import io.koraframework.cache.annotation.CacheInvalidateAll
import io.koraframework.cache.annotation.CachePut
import io.koraframework.cache.annotation.Cacheable
import io.koraframework.cache.symbol.processor.testcache.DummyCache11
import java.math.BigDecimal

open class CacheableSyncOne {
    var value = "1"

    @Cacheable(value = DummyCache11::class)
    open fun getValue(arg1: String): String {
        return value
    }

    @CachePut(value = DummyCache11::class, args = ["arg1"])
    open fun putValue(arg2: BigDecimal?, arg3: String?, arg1: String): String {
        return value
    }

    @CacheInvalidate(value = DummyCache11::class)
    open fun evictValue(arg1: String) {
    }

    @CacheInvalidateAll(DummyCache11::class)
    open fun evictAll() {
    }
}

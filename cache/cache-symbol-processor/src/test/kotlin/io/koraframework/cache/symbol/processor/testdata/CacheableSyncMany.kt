package io.koraframework.cache.symbol.processor.testdata

import io.koraframework.cache.annotation.CacheInvalidate
import io.koraframework.cache.annotation.CacheInvalidateAll
import io.koraframework.cache.annotation.CachePut
import io.koraframework.cache.annotation.Cacheable
import io.koraframework.cache.annotation.Cacheables
import io.koraframework.cache.symbol.processor.testcache.DummyCache21
import io.koraframework.cache.symbol.processor.testcache.DummyCache22
import java.math.BigDecimal

open class CacheableSyncMany {
    var value = "1"

    @Cacheables(value = [
        Cacheable(value = DummyCache21::class),
        Cacheable(value = DummyCache22::class)
    ])
    open fun getValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }

    @CachePut(value = DummyCache21::class, args = ["arg1", "arg2"])
    @CachePut(value = DummyCache22::class, args = ["arg1", "arg2"])
    open fun putValue(arg2: BigDecimal?, arg3: String?, arg1: String?): String {
        return value
    }

    @CacheInvalidate(DummyCache21::class)
    @CacheInvalidate(DummyCache22::class)
    open fun evictValue(arg1: String?, arg2: BigDecimal?) {
    }

    @CacheInvalidateAll(DummyCache21::class)
    @CacheInvalidateAll(DummyCache22::class)
    open fun evictAll() {
    }
}

package io.koraframework.cache.symbol.processor.testdata

import io.koraframework.cache.annotation.CacheInvalidate
import io.koraframework.cache.annotation.CacheInvalidateAll
import io.koraframework.cache.annotation.CachePut
import io.koraframework.cache.annotation.Cacheable
import io.koraframework.cache.symbol.processor.testcache.DummyCache21
import java.math.BigDecimal

open class CacheableSync {
    var value = "1"

    @Cacheable(value = DummyCache21::class)
    open fun getValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }

    @CachePut(value = DummyCache21::class, args = ["arg1", "arg2"])
    open fun putValue(arg2: BigDecimal?, arg3: String?, arg1: String?): String {
        return value
    }

    @CacheInvalidate(value = DummyCache21::class)
    open fun evictValue(arg1: String?, arg2: BigDecimal?) {
    }

    @CacheInvalidateAll(DummyCache21::class)
    open fun evictAll() {
    }

    // @Throws here is a typealias.
    // Should compile normally.
    // Method is not used in tests, only in processing
    @Throws
    @CacheInvalidateAll(DummyCache21::class)
    open fun throws1() {
    }

    // @Throws here is a typealias.
    // Should compile normally.
    // Method is not used in tests, only in processing
    @Throws
    @CachePut(value = DummyCache21::class, args = ["arg1", "arg2"])
    open fun throws2(arg2: BigDecimal?, arg3: String?, arg1: String?): String {
        return value
    }

    // @Throws here is a typealias.
    // Should compile normally.
    // Method is not used in tests, only in processing
    @Throws
    @Cacheable(value = DummyCache21::class)
    open fun throws3(arg1: String?, arg2: BigDecimal?): String {
        return value
    }
}

package ru.tinkoff.kora.cache.symbol.processor.testdata

import ru.tinkoff.kora.cache.annotation.CacheInvalidate
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache21
import java.math.BigDecimal

open class CacheableSync {
    var value = "1"

    @Cacheable(value = DummyCache21::class)
    open fun getValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }

    @CachePut(value = DummyCache21::class, parameters = ["arg1", "arg2"])
    open fun putValue(arg2: BigDecimal?, arg3: String?, arg1: String?): String {
        return value
    }

    @CacheInvalidate(value = DummyCache21::class)
    open fun evictValue(arg1: String?, arg2: BigDecimal?) {
    }

    @CacheInvalidate(value = DummyCache21::class, invalidateAll = true)
    open fun evictAll() {
    }

    // @Throws here is a typealias.
    // Should compile normally.
    // Method is not used in tests, only in processing
    @Throws
    @CacheInvalidate(value = DummyCache21::class, invalidateAll = true)
    open fun throws1() {
    }

    // @Throws here is a typealias.
    // Should compile normally.
    // Method is not used in tests, only in processing
    @Throws
    @CachePut(value = DummyCache21::class, parameters = ["arg1", "arg2"])
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

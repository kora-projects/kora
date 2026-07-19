package io.koraframework.cache.symbol.processor.testdata

import io.koraframework.cache.annotation.CacheInvalidate
import io.koraframework.cache.annotation.CacheInvalidateAll
import io.koraframework.cache.annotation.CacheMode
import io.koraframework.cache.annotation.CachePut
import io.koraframework.cache.annotation.Cacheable
import io.koraframework.cache.symbol.processor.testcache.DummyCache21
import io.koraframework.cache.symbol.processor.testcache.DummyCache22
import java.math.BigDecimal

open class CacheableAsync {

    @Cacheable(value = DummyCache22::class, mode = CacheMode.ASYNC)
    open fun getValue(arg1: String?, arg2: BigDecimal?): String {
        return "1"
    }

    @Cacheable(value = DummyCache21::class, mode = CacheMode.ASYNC)
    open fun getCaffeineValue(arg1: String?, arg2: BigDecimal?): String {
        return "1"
    }

    @CachePut(value = DummyCache22::class, args = ["arg1", "arg2"], mode = CacheMode.ASYNC)
    open fun putValue(arg1: String?, arg2: BigDecimal?): String {
        return "1"
    }

    @CacheInvalidate(value = DummyCache22::class, mode = CacheMode.ASYNC)
    open fun evictValue(arg1: String?, arg2: BigDecimal?) {
    }

    @CacheInvalidateAll(value = DummyCache22::class, mode = CacheMode.ASYNC)
    open fun evictAll() {
    }
}

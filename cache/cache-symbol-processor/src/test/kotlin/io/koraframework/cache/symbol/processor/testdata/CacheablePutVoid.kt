package io.koraframework.cache.symbol.processor.testdata

import io.koraframework.cache.annotation.CachePut
import io.koraframework.cache.annotation.Cacheable
import io.koraframework.cache.symbol.processor.testcache.DummyCache21
import java.math.BigDecimal

class CacheablePutVoid {
    var value = "1"

    @Cacheable(value = DummyCache21::class)
    fun getValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }

    @CachePut(value = DummyCache21::class, args = ["arg1", "arg2"])
    fun putValue(arg1: String?, arg2: BigDecimal?) {
    }
}

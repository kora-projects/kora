package io.koraframework.cache.symbol.processor.testdata

import io.koraframework.cache.annotation.CachePut
import io.koraframework.cache.symbol.processor.testcache.DummyCache21
import java.math.BigDecimal

class CacheableNameInvalid {
    var value = "1"

    @CachePut(value = DummyCache21::class)
    fun putValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }
}

package ru.tinkoff.kora.cache.symbol.processor.testdata

import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache2
import java.math.BigDecimal

class CacheableNameInvalid {
    var value = "1"

    @CachePut(value = DummyCache2::class)
    fun putValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }
}

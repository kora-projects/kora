package ru.tinkoff.kora.cache.symbol.processor.testdata

import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache21
import java.math.BigDecimal

class CacheableNameInvalid {
    var value = "1"

    @CachePut(value = DummyCache21::class)
    fun putValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }
}

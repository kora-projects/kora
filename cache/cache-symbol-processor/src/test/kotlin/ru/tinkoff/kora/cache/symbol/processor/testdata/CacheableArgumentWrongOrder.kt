package ru.tinkoff.kora.cache.symbol.processor.testdata

import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache2
import java.math.BigDecimal

class CacheableArgumentWrongOrder {
    var value = "1"

    @Cacheable(value = DummyCache2::class)
    fun getValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }

    @CachePut(value = DummyCache2::class, parameters = ["arg2", "arg1"])
    fun putValue(arg2: BigDecimal?, arg3: String?, arg1: String?): String {
        return value
    }
}

package ru.tinkoff.kora.cache.symbol.processor.testdata

import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCacheManager
import java.math.BigDecimal

class CacheableTargetNameInvalid {
    var value = "1"

    @CachePut(name = "my-cache", tags = [DummyCacheManager::class])
    fun putValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }
}

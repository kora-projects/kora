package ru.tinkoff.kora.cache.symbol.processor.testdata

import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCacheManager
import java.math.BigDecimal

class CacheableTargetPutVoid {
    var value = "1"

    @Cacheable(name = "sync_cache", tags = [DummyCacheManager::class])
    fun getValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }

    @CachePut(name = "sync_cache", tags = [DummyCacheManager::class], parameters = ["arg1", "arg2"])
    fun putValue(arg1: String?, arg2: BigDecimal?) {
    }
}

package ru.tinkoff.kora.cache.symbol.processor.testdata

import ru.tinkoff.kora.cache.annotation.CacheInvalidate
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCacheManager
import java.math.BigDecimal

open class CacheableTargetSync {
    var value = "1"

    @Cacheable(name = "sync_cache", tags = [DummyCacheManager::class])
    open fun getValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }

    @CachePut(name = "sync_cache", tags = [DummyCacheManager::class], parameters = ["arg1", "arg2"])
    open fun putValue(arg2: BigDecimal?, arg3: String?, arg1: String?): String {
        return value
    }

    @CacheInvalidate(name = "sync_cache", tags = [DummyCacheManager::class])
    open fun evictValue(arg1: String?, arg2: BigDecimal?) {
    }

    @CacheInvalidate(name = "sync_cache", tags = [DummyCacheManager::class], invalidateAll = true)
    open fun evictAll() {
    }
}

package ru.tinkoff.kora.cache.symbol.processor.testdata.suspended

import kotlinx.coroutines.delay
import ru.tinkoff.kora.cache.annotation.CacheInvalidate
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCacheManager
import java.math.BigDecimal

open class CacheableTargetSuspendMany {
    var value = "1"

    @Cacheable(name = "suspend_cache", tags = [DummyCacheManager::class])
    @Cacheable(name = "suspend_cache_2", tags = [DummyCacheManager::class])
    open suspend fun getValue(arg1: String?, arg2: BigDecimal?): String {
        delay(10)
        return value
    }

    @CachePut(name = "suspend_cache", tags = [DummyCacheManager::class], parameters = ["arg1", "arg2"])
    @CachePut(name = "suspend_cache_2", tags = [DummyCacheManager::class], parameters = ["arg1", "arg2"])
    open suspend fun putValue(arg2: BigDecimal?, arg3: String?, arg1: String?): String {
        delay(10)
        return value
    }

    @CacheInvalidate(name = "suspend_cache", tags = [DummyCacheManager::class])
    @CacheInvalidate(name = "suspend_cache_2", tags = [DummyCacheManager::class])
    open suspend fun evictValue(arg1: String?, arg2: BigDecimal?) {
        delay(10)
    }

    @CacheInvalidate(name = "suspend_cache", tags = [DummyCacheManager::class], invalidateAll = true)
    @CacheInvalidate(name = "suspend_cache_2", tags = [DummyCacheManager::class], invalidateAll = true)
    open suspend fun evictAll() {
        delay(10)
    }
}

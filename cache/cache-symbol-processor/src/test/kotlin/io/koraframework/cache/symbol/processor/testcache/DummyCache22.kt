package io.koraframework.cache.symbol.processor.testcache

import io.koraframework.cache.annotation.Cache
import io.koraframework.cache.redis.RedisCache
import java.math.BigDecimal

@Cache("dummy22")
interface DummyCache22 : RedisCache<DummyCache22.Key, String> {

    data class Key(val k1: String, val k2: BigDecimal)
}

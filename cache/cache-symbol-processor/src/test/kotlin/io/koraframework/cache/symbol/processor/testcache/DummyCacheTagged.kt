package io.koraframework.cache.symbol.processor.testcache

import io.koraframework.cache.annotation.Cache
import io.koraframework.cache.redis.RedisCache
import io.koraframework.json.common.annotation.Json
import java.math.BigDecimal

@Cache("dummy22")
interface DummyCacheTagged : RedisCache<@Json DummyCacheTagged.Key, @Json DummyCacheTagged.Key> {

    data class Key(val k1: String, val k2: BigDecimal?)
}

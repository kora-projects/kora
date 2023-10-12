package ru.tinkoff.kora.cache.symbol.processor.testcache

import ru.tinkoff.kora.cache.annotation.Cache
import ru.tinkoff.kora.cache.redis.RedisCache
import ru.tinkoff.kora.json.common.annotation.Json
import java.math.BigDecimal

@Cache("dummy22")
interface DummyCacheTagged : RedisCache<@Json DummyCacheTagged.Key, @Json DummyCacheTagged.Key> {

    data class Key(val k1: String, val k2: BigDecimal?)
}

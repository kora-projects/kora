package ru.tinkoff.kora.cache.symbol.processor.testcache

import ru.tinkoff.kora.cache.annotation.Cache
import ru.tinkoff.kora.cache.redis.RedisCache
import java.math.BigDecimal

@Cache("dummy22")
interface DummyCache22 : RedisCache<DummyCache22.Key, String> {

    data class Key(val k1: String, val k2: BigDecimal)
}

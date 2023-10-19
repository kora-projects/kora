package ru.tinkoff.kora.cache.symbol.processor.testcache

import ru.tinkoff.kora.cache.annotation.Cache
import ru.tinkoff.kora.cache.caffeine.CaffeineCache
import java.math.BigDecimal

@Cache("dummy21")
interface DummyCache21 : CaffeineCache<DummyCache21.Key, String> {

    data class Key(val k1: String, val k2: BigDecimal)
}

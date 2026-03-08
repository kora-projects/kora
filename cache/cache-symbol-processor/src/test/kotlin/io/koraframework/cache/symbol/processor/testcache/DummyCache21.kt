package io.koraframework.cache.symbol.processor.testcache

import io.koraframework.cache.annotation.Cache
import io.koraframework.cache.caffeine.CaffeineCache
import java.math.BigDecimal

@Cache("dummy21")
interface DummyCache21 : CaffeineCache<DummyCache21.Key, String> {

    data class Key(val k1: String, val k2: BigDecimal)
}

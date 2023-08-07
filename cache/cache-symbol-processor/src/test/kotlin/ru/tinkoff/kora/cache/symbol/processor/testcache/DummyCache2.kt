package ru.tinkoff.kora.cache.symbol.processor.testcache

import ru.tinkoff.kora.cache.CacheKey
import ru.tinkoff.kora.cache.annotation.Cache
import ru.tinkoff.kora.cache.caffeine.CaffeineCache
import java.math.BigDecimal

@Cache("dummy2")
interface DummyCache2 : CaffeineCache<CacheKey.Key2<String?, BigDecimal?>, String>

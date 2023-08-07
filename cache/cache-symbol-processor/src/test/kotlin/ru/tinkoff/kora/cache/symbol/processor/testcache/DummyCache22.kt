package ru.tinkoff.kora.cache.symbol.processor.testcache

import ru.tinkoff.kora.cache.CacheKey
import ru.tinkoff.kora.cache.annotation.Cache
import ru.tinkoff.kora.cache.caffeine.CaffeineCache
import java.math.BigDecimal

@Cache("dummy22")
interface DummyCache22 : CaffeineCache<CacheKey.Key2<String?, BigDecimal?>, String>

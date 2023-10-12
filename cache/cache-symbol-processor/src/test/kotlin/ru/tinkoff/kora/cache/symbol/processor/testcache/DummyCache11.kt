package ru.tinkoff.kora.cache.symbol.processor.testcache

import ru.tinkoff.kora.cache.annotation.Cache
import ru.tinkoff.kora.cache.caffeine.CaffeineCache

@Cache("dummy11")
interface DummyCache11 : CaffeineCache<String, String>


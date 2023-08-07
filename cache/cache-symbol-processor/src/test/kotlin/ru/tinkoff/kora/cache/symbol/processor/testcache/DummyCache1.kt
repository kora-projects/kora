package ru.tinkoff.kora.cache.symbol.processor.testcache

import ru.tinkoff.kora.cache.annotation.Cache
import ru.tinkoff.kora.cache.caffeine.CaffeineCache

@Cache("dummy1")
interface DummyCache1 : CaffeineCache<String, String>


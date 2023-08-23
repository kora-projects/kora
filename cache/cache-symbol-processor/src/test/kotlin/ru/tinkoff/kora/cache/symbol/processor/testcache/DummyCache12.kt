package ru.tinkoff.kora.cache.symbol.processor.testcache

import ru.tinkoff.kora.cache.annotation.Cache
import ru.tinkoff.kora.cache.caffeine.CaffeineCache
import ru.tinkoff.kora.cache.redis.RedisCache

@Cache("dummy12")
interface DummyCache12 : RedisCache<String, String>


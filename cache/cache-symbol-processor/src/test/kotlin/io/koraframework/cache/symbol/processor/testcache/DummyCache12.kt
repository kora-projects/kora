package io.koraframework.cache.symbol.processor.testcache

import io.koraframework.cache.annotation.Cache
import io.koraframework.cache.redis.RedisCache

@Cache("dummy12")
interface DummyCache12 : RedisCache<String, String>


package io.koraframework.cache.symbol.processor.testcache

import io.koraframework.cache.annotation.Cache
import io.koraframework.cache.caffeine.CaffeineCache

@Cache("dummy11")
interface DummyCache11 : CaffeineCache<String, String>


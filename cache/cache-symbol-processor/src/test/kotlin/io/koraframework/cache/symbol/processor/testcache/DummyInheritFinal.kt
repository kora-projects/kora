package io.koraframework.cache.symbol.processor.testcache

import io.koraframework.cache.annotation.Cache
import io.koraframework.cache.caffeine.CaffeineCache

interface DummyInheritFinal {

    @Cache("dummy")
    interface DummyChild : DummyParent {
        val some: String?
            get() = get("some")
    }

    interface DummyParent : CaffeineCache<String, String>
}

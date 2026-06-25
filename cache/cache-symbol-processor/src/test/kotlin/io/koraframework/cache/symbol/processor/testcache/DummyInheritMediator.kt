package io.koraframework.cache.symbol.processor.testcache

import io.koraframework.cache.annotation.Cache
import io.koraframework.cache.caffeine.CaffeineCache

interface DummyInheritMediator {

    @Cache("dummy")
    interface DummyChild : DummyParent<String, String> {
        val some: String?
            get() = get("some")
    }

    interface DummyParent<K, V> : CaffeineCache<K, V>
}

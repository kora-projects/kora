package io.koraframework.cache.annotation.processor.testcache;

import io.koraframework.cache.annotation.Cache;
import io.koraframework.cache.caffeine.CaffeineCache;

public interface DummyInheritFinal {

    @Cache("dummy")
    interface DummyChild extends DummyParent {

        default String getSome() {
            return get("some");
        }
    }

    interface DummyParent extends CaffeineCache<String, String> {

    }
}

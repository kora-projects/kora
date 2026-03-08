package io.koraframework.cache.annotation.processor.testdata.sync;

import io.koraframework.cache.annotation.Cache;
import io.koraframework.cache.caffeine.CaffeineCache;

@Cache("_1dummy")
public interface CacheableSyncWrongName extends CaffeineCache<String, String> {

}

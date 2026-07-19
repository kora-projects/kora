package io.koraframework.cache;

import io.koraframework.common.annotation.DefaultComponent;

public interface CacheCommonModule {

    @DefaultComponent
    default CacheAsyncExecutor cacheAsyncExecutor() {
        return new CacheAsyncExecutor();
    }
}

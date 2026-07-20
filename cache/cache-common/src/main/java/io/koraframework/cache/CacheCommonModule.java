package io.koraframework.cache;

import io.koraframework.cache.annotation.CacheMode;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;

import java.util.concurrent.Executor;

public interface CacheCommonModule {

    @Tag(CacheMode.class)
    @DefaultComponent
    default Executor cacheAsyncExecutor() {
        return new CacheAsyncExecutor();
    }
}

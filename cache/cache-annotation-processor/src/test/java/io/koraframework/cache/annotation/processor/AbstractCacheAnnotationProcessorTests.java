package io.koraframework.cache.annotation.processor;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;

public class AbstractCacheAnnotationProcessorTests extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() +
               """
                   import java.util.concurrent.CompletableFuture;
                   import java.util.concurrent.CompletionStage;
                   import io.koraframework.json.common.JsonNullable;
                   import org.jspecify.annotations.NonNull;
                   import org.jspecify.annotations.Nullable;
                   import io.koraframework.common.KoraApp;
                   import io.koraframework.common.Component;
                   import io.koraframework.common.annotation.Root;
                   import io.koraframework.cache.annotation.Cache;
                   import io.koraframework.cache.caffeine.CaffeineCache;
                   import io.koraframework.cache.annotation.CacheInvalidate;
                   import io.koraframework.cache.annotation.CachePut;
                   import io.koraframework.cache.annotation.Cacheable;
                   import java.math.BigDecimal;
                   import java.util.Optional;
                   """;
    }
}

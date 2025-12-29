package ru.tinkoff.kora.cache.annotation.processor;

import ru.tinkoff.kora.annotation.processor.common.AbstractAnnotationProcessorTest;

public class AbstractCacheAnnotationProcessorTests extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() +
               """
                   import java.util.concurrent.CompletableFuture;
                   import java.util.concurrent.CompletionStage;
                   import ru.tinkoff.kora.json.common.JsonNullable;
                   import org.jspecify.annotations.NonNull;
                   import org.jspecify.annotations.Nullable;
                   import ru.tinkoff.kora.common.KoraApp;
                   import ru.tinkoff.kora.common.Component;
                   import ru.tinkoff.kora.common.annotation.Root;
                   import ru.tinkoff.kora.cache.annotation.Cache;
                   import ru.tinkoff.kora.cache.caffeine.CaffeineCache;
                   import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
                   import ru.tinkoff.kora.cache.annotation.CachePut;
                   import ru.tinkoff.kora.cache.annotation.Cacheable;
                   import java.math.BigDecimal;
                   import java.util.Optional;
                   """;
    }
}

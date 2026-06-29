package ru.tinkoff.kora.cache.redis;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.cache.AsyncCache;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface RedisCache<K, V> extends AsyncCache<K, V> {

    @Nonnull
    V putExpireAfterWrite(@Nonnull K key,
                          @Nonnull V value,
                          @Nonnull Duration expireAfterWrite);

    @Nonnull
    Map<K, V> putExpireAfterWrite(@Nonnull Map<K, V> keyAndValues,
                                  @Nonnull Duration expireAfterWrite);

    @Nonnull
    CompletionStage<V> putAsyncExpireAfterWrite(@Nonnull K key,
                                                @Nonnull V value,
                                                @Nonnull Duration expireAfterWrite);

    @Nonnull
    CompletionStage<Map<K, V>> putAsyncExpireAfterWrite(@Nonnull Map<K, V> keyAndValues,
                                                        @Nonnull Duration expireAfterWrite);
}

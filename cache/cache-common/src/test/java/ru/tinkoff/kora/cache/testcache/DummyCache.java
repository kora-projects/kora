package ru.tinkoff.kora.cache.testcache;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.cache.AsyncCache;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DummyCache implements AsyncCache<String, String> {

    private final Map<String, String> cache = new HashMap<>();

    public DummyCache(String name) {

    }

    @Override
    public String get(@Nonnull String key) {
        return cache.get(key);
    }

    @Nonnull
    @Override
    public String put(@Nonnull String key, @Nonnull String value) {
        cache.put(key, value);
        return value;
    }

    @Override
    public String computeIfAbsent(@Nonnull String key, @Nonnull Function<String, String> mappingFunction) {
        return cache.computeIfAbsent(key, mappingFunction);
    }

    @Nonnull
    @Override
    public Map<String, String> computeIfAbsent(@Nonnull Collection<String> keys, @Nonnull Function<Set<String>, Map<String, String>> mappingFunction) {
        return keys.stream()
            .map(k -> Map.of(k, cache.computeIfAbsent(k, key -> mappingFunction.apply(Set.of(key)).get(key))))
            .flatMap(m -> m.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void invalidate(@Nonnull String key) {
        cache.remove(key);
    }

    @Override
    public void invalidateAll() {
        cache.clear();
    }

    @Nonnull
    @Override
    public CompletionStage<String> getAsync(@Nonnull String key) {
        return CompletableFuture.completedFuture(get(key));
    }

    @Nonnull
    @Override
    public CompletionStage<String> putAsync(@Nonnull String key, @Nonnull String value) {
        put(key, value);
        return CompletableFuture.completedFuture(value);
    }

    @Override
    public CompletionStage<String> computeIfAbsentAsync(@Nonnull String key, @Nonnull Function<String, CompletionStage<String>> mappingFunction) {
        return CompletableFuture.completedFuture(computeIfAbsent(key, (k) -> mappingFunction.apply(k).toCompletableFuture().join()));
    }

    @Nonnull
    @Override
    public CompletionStage<Map<String, String>> computeIfAbsentAsync(@Nonnull Collection<String> keys, @Nonnull Function<Set<String>, CompletionStage<Map<String, String>>> mappingFunction) {
        return CompletableFuture.completedFuture(computeIfAbsent(keys, (k) -> mappingFunction.apply(k).toCompletableFuture().join()));
    }

    @Nonnull
    @Override
    public CompletionStage<Boolean> invalidateAsync(@Nonnull String key) {
        invalidate(key);
        return CompletableFuture.completedFuture(true);
    }

    @Nonnull
    @Override
    public CompletionStage<Boolean> invalidateAllAsync() {
        invalidateAll();
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public void invalidate(@Nonnull Collection<String> keys) {
        for (String key : keys) {
            invalidate(key);
        }
    }

    @Override
    public CompletionStage<Boolean> invalidateAsync(@Nonnull Collection<String> keys) {
        for (String key : keys) {
            invalidate(key);
        }
        return CompletableFuture.completedFuture(true);
    }

    @Nonnull
    @Override
    public Map<String, String> get(@Nonnull Collection<String> keys) {
        return keys.stream()
            .collect(Collectors.toMap(k -> k, cache::get));
    }

    @Nonnull
    @Override
    public CompletionStage<Map<String, String>> getAsync(@Nonnull Collection<String> keys) {
        return CompletableFuture.completedFuture(get(keys));
    }

    @Nonnull
    @Override
    public CompletionStage<Map<String, String>> putAsync(@Nonnull Map<String, String> keyAndValues) {
        return CompletableFuture.completedFuture(put(keyAndValues));
    }

    @Nonnull
    @Override
    public Map<String, String> put(@Nonnull Map<String, String> keyAndValues) {
        cache.putAll(keyAndValues);
        return keyAndValues;
    }
}

package ru.tinkoff.kora.cache.testcache;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.Cache;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DummyCache implements Cache<String, String> {

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
    public Mono<String> getAsync(@Nonnull String key) {
        return Mono.justOrEmpty(get(key));
    }

    @Nonnull
    @Override
    public Mono<String> putAsync(@Nonnull String key, @Nonnull String value) {
        put(key, value);
        return Mono.just(value);
    }

    @Override
    public Mono<String> computeIfAbsentAsync(@Nonnull String key, @Nonnull Function<String, Mono<String>> mappingFunction) {
        return Mono.just(computeIfAbsent(key, (k) -> mappingFunction.apply(k).block()));
    }

    @Nonnull
    @Override
    public Mono<Map<String, String>> computeIfAbsentAsync(@Nonnull Collection<String> keys, @Nonnull Function<Set<String>, Mono<Map<String, String>>> mappingFunction) {
        return Mono.just(computeIfAbsent(keys, (k) -> mappingFunction.apply(k).block()));
    }

    @Nonnull
    @Override
    public Mono<Boolean> invalidateAsync(@Nonnull String key) {
        invalidate(key);
        return Mono.just(true);
    }

    @Nonnull
    @Override
    public Mono<Boolean> invalidateAllAsync() {
        invalidateAll();
        return Mono.just(true);
    }

    @Override
    public void invalidate(@Nonnull Collection<String> keys) {
        for (String key : keys) {
            invalidate(key);
        }
    }

    @Override
    public Mono<Boolean> invalidateAsync(@Nonnull Collection<String> keys) {
        for (String key : keys) {
            invalidate(key);
        }
        return Mono.just(true);
    }

    @Nonnull
    @Override
    public Map<String, String> get(@Nonnull Collection<String> keys) {
        return keys.stream()
            .collect(Collectors.toMap(k -> k, cache::get));
    }

    @Nonnull
    @Override
    public Mono<Map<String, String>> getAsync(@Nonnull Collection<String> keys) {
        return Mono.just(get(keys));
    }
}

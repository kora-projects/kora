package ru.tinkoff.kora.cache.testcache;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.cache.Cache;

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

    @Override
    public void invalidate(@Nonnull Collection<String> keys) {
        for (String key : keys) {
            invalidate(key);
        }
    }

    @Nonnull
    @Override
    public Map<String, String> get(@Nonnull Collection<String> keys) {
        return keys.stream()
            .collect(Collectors.toMap(k -> k, cache::get));
    }

    @Nonnull
    @Override
    public Map<String, String> put(@Nonnull Map<String, String> keyAndValues) {
        cache.putAll(keyAndValues);
        return keyAndValues;
    }
}

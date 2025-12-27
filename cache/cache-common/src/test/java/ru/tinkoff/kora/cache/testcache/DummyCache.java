package ru.tinkoff.kora.cache.testcache;

import org.jspecify.annotations.NullMarked;
import ru.tinkoff.kora.cache.Cache;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@NullMarked
public class DummyCache implements Cache<String, String> {

    private final Map<String, String> cache = new HashMap<>();

    public DummyCache(String name) {

    }

    @Override
    public String get(String key) {
        return cache.get(key);
    }

    @Override
    public String put(String key, String value) {
        cache.put(key, value);
        return value;
    }

    @Override
    public String computeIfAbsent(String key, Function<String, String> mappingFunction) {
        return cache.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public Map<String, String> computeIfAbsent(Collection<String> keys, Function<Set<String>, Map<String, String>> mappingFunction) {
        return keys.stream()
            .map(k -> Map.of(k, cache.computeIfAbsent(k, key -> mappingFunction.apply(Set.of(key)).get(key))))
            .flatMap(m -> m.entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void invalidate(String key) {
        cache.remove(key);
    }

    @Override
    public void invalidateAll() {
        cache.clear();
    }

    @Override
    public void invalidate(Collection<String> keys) {
        for (String key : keys) {
            invalidate(key);
        }
    }

    @Override
    public Map<String, String> get(Collection<String> keys) {
        return keys.stream()
            .collect(Collectors.toMap(k -> k, cache::get));
    }

    @Override
    public Map<String, String> put(Map<String, String> keyAndValues) {
        cache.putAll(keyAndValues);
        return keyAndValues;
    }
}

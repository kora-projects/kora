package ru.tinkoff.kora.cache.annotation.processor.testcache;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.Cache;
import ru.tinkoff.kora.cache.LoadableCache;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class DummyCache<K, V> implements Cache<K, V>, LoadableCache<K, V> {

    private final String name;
    private final Map<String, V> cache = new HashMap<>();

    public DummyCache(String name) {
        this.name = name;
    }

    @Nonnull
    public String origin() {
        return "dummy";
    }

    @Nonnull
    public String name() {
        return name;
    }

    @Override
    public V get(@Nonnull K key) {
        return cache.get(key.toString());
    }

    @Nonnull
    @Override
    public V put(@Nonnull K key, @Nonnull V value) {
        cache.put(key.toString(), value);
        return value;
    }

    @Override
    public void invalidate(@Nonnull K key) {
        cache.remove(key.toString());
    }

    @Override
    public void invalidateAll() {
        cache.clear();
    }

    @Nonnull
    @Override
    public Mono<V> getAsync(@Nonnull K key) {
        return Mono.justOrEmpty(get(key));
    }

    @Nonnull
    @Override
    public Mono<V> putAsync(@Nonnull K key, @Nonnull V value) {
        put(key, value);
        return Mono.just(value);
    }

    @Nonnull
    @Override
    public Mono<Void> invalidateAsync(@Nonnull K key) {
        invalidate(key);
        return Mono.empty();
    }

    @Nonnull
    @Override
    public Mono<Void> invalidateAllAsync() {
        invalidateAll();
        return Mono.empty();
    }

    public void reset() {
        cache.clear();;
    }

    public boolean isEmpty() {
        return cache.isEmpty();
    }
}

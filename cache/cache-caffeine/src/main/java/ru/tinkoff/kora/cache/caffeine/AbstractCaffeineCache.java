package ru.tinkoff.kora.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@NullMarked
public abstract class AbstractCaffeineCache<K, V> implements CaffeineCache<K, V> {

    private final Cache<K, V> caffeine;
    private final Logger log;

    protected AbstractCaffeineCache(String name, CaffeineCacheConfig config, CaffeineCacheFactory factory) {
        this.caffeine = factory.build(name, config);
        if (config.telemetry().logging().enabled()) {
            this.log = LoggerFactory.getLogger("ru.tinkoff.kora.cache.caffeine." + name);
        } else {
            this.log = NOPLogger.NOP_LOGGER;
        }
    }

    @Override
    @Nullable
    public V get(K key) {
        if (key == null) {
            return null;
        }
        log.trace("Operation 'GET' started");
        V value = caffeine.getIfPresent(key);
        if (value == null) {
            log.trace("Operation 'GET' didn't return a value");
        } else {
            log.debug("Operation 'GET' returned a value");
        }
        return value;
    }

    @Override
    public Map<K, V> get(Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        log.trace("Operation 'GET_MANY' started");
        var values = caffeine.getAllPresent(keys);
        log.trace("Operation 'GET_MANY' completed");
        return values;
    }

    @Override
    public Map<K, V> getAll() {
        log.trace("Operation 'GET_ALL' started");
        var values = Collections.unmodifiableMap(caffeine.asMap());
        log.trace("Operation 'GET_ALL' completed");
        return values;
    }

    @Override
    public V computeIfAbsent(K key, Function<K, @Nullable V> mappingFunction) {
        if (key == null) {
            return mappingFunction.apply(key);
        }

        log.trace("Operation 'COMPUTE_IF_ABSENT' started");
        var value = caffeine.get(key, mappingFunction);
        log.trace("Operation 'COMPUTE_IF_ABSENT' completed");
        return value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<K, V> computeIfAbsent(Collection<K> keys, Function<Set<K>, Map<K, V>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            return mappingFunction.apply(Collections.emptySet());
        }

        log.trace("Operation 'COMPUTE_IF_ABSENT_MANY' started");
        var value = caffeine.getAll(keys, ks -> mappingFunction.apply((Set<K>) ks));
        log.trace("Operation 'COMPUTE_IF_ABSENT_MANY' completed");
        return value;
    }

    public V put(K key, V value) {
        if (key == null || value == null) {
            return value;
        }

        log.trace("Operation 'PUT' started");
        caffeine.put(key, value);
        log.trace("Operation 'PUT' completed");
        return value;
    }

    @Override
    public Map<K, V> put(Map<K, V> keyAndValues) {
        if (keyAndValues == null || keyAndValues.isEmpty()) {
            return Collections.emptyMap();
        }

        log.trace("Operation 'PUT_MANY' started");
        caffeine.putAll(keyAndValues);
        log.trace("Operation 'PUT_MANY' completed");
        return keyAndValues;
    }

    @Override
    public void invalidate(K key) {
        if (key == null) {
            return;
        }
        log.trace("Operation 'INVALIDATE' started");
        caffeine.invalidate(key);
        log.trace("Operation 'INVALIDATE' completed");
    }

    @Override
    public void invalidate(Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        log.trace("Operation 'INVALIDATE_MANY' started");
        caffeine.invalidateAll(keys);
        log.trace("Operation 'INVALIDATE_MANY' completed");
    }

    @Override
    public void invalidateAll() {
        log.trace("Operation 'INVALIDATE_ALL' started");
        caffeine.invalidateAll();
        log.trace("Operation 'INVALIDATE_ALL' completed");
    }
}

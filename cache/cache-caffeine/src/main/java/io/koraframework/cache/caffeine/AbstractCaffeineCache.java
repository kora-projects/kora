package io.koraframework.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetry;
import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetryFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetry.Operation.*;

@NullMarked
public abstract class AbstractCaffeineCache<K, V> implements CaffeineCache<K, V> {

    private final Cache<K, V> caffeine;
    private final CaffeineCacheTelemetry telemetry;

    protected AbstractCaffeineCache(String cacheConfigPath,
                                    CaffeineCacheConfig config,
                                    CaffeineCacheFactory factory,
                                    CaffeineCacheTelemetryFactory telemetryFactory) {
        this.caffeine = factory.build(cacheConfigPath, config);
        this.telemetry = telemetryFactory.get(cacheConfigPath, getClass(), config.telemetry());
    }

    @Override
    @Nullable
    public V get(K key) {
        if (key == null) {
            return null;
        }

        var observation = this.telemetry.observe(GET);
        observation.observeKey(key);
        try {
            var value = caffeine.getIfPresent(key);
            observation.observeValue(value);
            return value;
        } catch (Exception e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }

    @Override
    public Map<K, V> get(Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        var observation = this.telemetry.observe(GET_MANY);
        observation.observeKeys(keys);
        try {
            var values = caffeine.getAllPresent(keys);
            observation.observeValues(values);
            return values;
        } catch (Exception e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }

    @Override
    public Map<K, V> getAll() {
        var observation = this.telemetry.observe(GET_ALL);
        try {
            var values = Collections.unmodifiableMap(caffeine.asMap());
            observation.observeValues(values);
            return values;
        } catch (Exception e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }

    @Override
    public V computeIfAbsent(K key, Function<K, @Nullable V> mappingFunction) {
        if (key == null) {
            return mappingFunction.apply(key);
        }

        var observation = this.telemetry.observe(COMPUTE_IF_ABSENT);
        observation.observeKey(key);
        try {
            var value = caffeine.get(key, mappingFunction);
            observation.observeValue(value);
            return value;
        } catch (Exception e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<K, V> computeIfAbsent(Collection<K> keys, Function<Set<K>, Map<K, V>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            return mappingFunction.apply(Collections.emptySet());
        }

        var observation = this.telemetry.observe(COMPUTE_IF_ABSENT_MANY);
        observation.observeKeys(keys);
        try {
            var value = caffeine.getAll(keys, ks -> mappingFunction.apply((Set<K>) ks));
            if (value == null) {
                value = Collections.emptyMap();
            }
            observation.observeValues(value);
            return value;
        } catch (Exception e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }

    public V put(K key, V value) {
        if (key == null || value == null) {
            return value;
        }

        var observation = this.telemetry.observe(PUT);
        observation.observeKey(key);
        observation.observeValue(value);
        try {
            caffeine.put(key, value);
            return value;
        } catch (Exception e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }

    @Override
    public Map<K, V> put(Map<K, V> keyAndValues) {
        if (keyAndValues == null || keyAndValues.isEmpty()) {
            return Collections.emptyMap();
        }

        var observation = this.telemetry.observe(PUT_MANY);
        observation.observeKeys(keyAndValues.keySet());
        observation.observeValues(keyAndValues);
        try {
            caffeine.putAll(keyAndValues);
            return keyAndValues;
        } catch (Exception e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }

    @Override
    public void invalidate(K key) {
        if (key == null) {
            return;
        }

        var observation = this.telemetry.observe(INVALIDATE);
        observation.observeKey(key);
        try {
            caffeine.invalidate(key);
        } catch (Exception e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }

    @Override
    public void invalidate(Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        var observation = this.telemetry.observe(INVALIDATE_MANY);
        observation.observeKeys(keys);
        try {
            caffeine.invalidateAll(keys);
        } catch (Exception e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }

    @Override
    public void invalidateAll() {
        var observation = this.telemetry.observe(INVALIDATE_ALL);
        try {
            observation.observeKeys(List.of());
            caffeine.invalidateAll();
        } catch (Exception e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }
}

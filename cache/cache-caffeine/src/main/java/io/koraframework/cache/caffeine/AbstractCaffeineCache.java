package io.koraframework.cache.caffeine;

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

    private final String cacheName;
    private final Cache<K, V> caffeine;
    private final Logger logger;

    protected AbstractCaffeineCache(String cacheName,
                                    String cacheImpl,
                                    CaffeineCacheConfig config,
                                    CaffeineCacheFactory factory) {
        this.cacheName = cacheName;
        this.caffeine = factory.build(cacheName, config);
        this.logger = config.telemetry().logging().enabled()
            ? LoggerFactory.getLogger(cacheImpl)
            : NOPLogger.NOP_LOGGER;
    }

    @Override
    @Nullable
    public V get(K key) {
        if (key == null) {
            return null;
        }

        V value = caffeine.getIfPresent(key);
        if (value == null) {
            logger.atTrace()
                .addKeyValue("cacheName", cacheName)
                .addKeyValue("operation", "GET")
                .addKeyValue("key", key)
                .log("Caffeine Cache operation didn't retrieved value");
        } else {
            logger.atDebug()
                .addKeyValue("cacheName", cacheName)
                .addKeyValue("operation", "GET")
                .addKeyValue("key", key)
                .log("Caffeine Cache operation retrieved value");
        }
        return value;
    }

    @Override
    public Map<K, V> get(Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        var values = caffeine.getAllPresent(keys);
        logger.atTrace()
            .addKeyValue("cacheName", cacheName)
            .addKeyValue("operation", "GET_MANY")
            .addKeyValue("keys", keys.size())
            .addKeyValue("values", values.size())
            .log("Caffeine Cache operation completed");
        return values;
    }

    @Override
    public Map<K, V> getAll() {
        var values = Collections.unmodifiableMap(caffeine.asMap());
        logger.atTrace()
            .addKeyValue("cacheName", cacheName)
            .addKeyValue("operation", "GET_ALL")
            .addKeyValue("values", values.size())
            .log("Caffeine Cache operation completed");
        return values;
    }

    @Override
    public V computeIfAbsent(K key, Function<K, @Nullable V> mappingFunction) {
        if (key == null) {
            logger.atTrace()
                .addKeyValue("cacheName", cacheName)
                .addKeyValue("operation", "COMPUTE_IF_ABSENT")
                .log("Caffeine Cache operation empty key provided, executing mapping function...");
            return mappingFunction.apply(key);
        }

        logger.atTrace()
            .addKeyValue("cacheName", cacheName)
            .addKeyValue("operation", "COMPUTE_IF_ABSENT")
            .addKeyValue("key", key)
            .log("Caffeine Cache operation started...");

        var value = caffeine.get(key, mappingFunction);
        if (value == null) {
            logger.atTrace()
                .addKeyValue("cacheName", cacheName)
                .addKeyValue("operation", "COMPUTE_IF_ABSENT")
                .addKeyValue("key", key)
                .log("Caffeine Cache operation completed without any value");
        } else {
            logger.atTrace()
                .addKeyValue("cacheName", cacheName)
                .addKeyValue("operation", "COMPUTE_IF_ABSENT")
                .addKeyValue("key", key)
                .log("Caffeine Cache operation completed with value");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<K, V> computeIfAbsent(Collection<K> keys, Function<Set<K>, Map<K, V>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            logger.atTrace()
                .addKeyValue("cacheName", cacheName)
                .addKeyValue("operation", "COMPUTE_IF_ABSENT_MANY")
                .log("Caffeine Cache operation empty keys provided, executing mapping function...");
            return mappingFunction.apply(Collections.emptySet());
        }

        logger.atTrace()
            .addKeyValue("cacheName", cacheName)
            .addKeyValue("operation", "COMPUTE_IF_ABSENT_MANY")
            .addKeyValue("keys", keys.size())
            .log("Caffeine Cache operation started...");

        var value = caffeine.getAll(keys, ks -> mappingFunction.apply((Set<K>) ks));
        if (value == null) {
            value = Collections.emptyMap();
        }

        logger.atTrace()
            .addKeyValue("cacheName", cacheName)
            .addKeyValue("operation", "COMPUTE_IF_ABSENT_MANY")
            .addKeyValue("keys", keys.size())
            .addKeyValue("values", value.size())
            .log("Caffeine Cache operation completed");
        return value;
    }

    public V put(K key, V value) {
        if (key == null || value == null) {
            return value;
        }

        caffeine.put(key, value);
        logger.atTrace()
            .addKeyValue("cacheName", cacheName)
            .addKeyValue("operation", "PUT")
            .addKeyValue("key", key)
            .log("Caffeine Cache operation completed");
        return value;
    }

    @Override
    public Map<K, V> put(Map<K, V> keyAndValues) {
        if (keyAndValues == null || keyAndValues.isEmpty()) {
            return Collections.emptyMap();
        }

        caffeine.putAll(keyAndValues);
        logger.atTrace()
            .addKeyValue("cacheName", cacheName)
            .addKeyValue("operation", "PUT_MANY")
            .addKeyValue("keys", keyAndValues.size())
            .log("Caffeine Cache operation completed");

        return keyAndValues;
    }

    @Override
    public void invalidate(K key) {
        if (key == null) {
            return;
        }

        caffeine.invalidate(key);
        logger.atTrace()
            .addKeyValue("cacheName", cacheName)
            .addKeyValue("operation", "INVALIDATE")
            .addKeyValue("key", key)
            .log("Caffeine Cache operation completed");
    }

    @Override
    public void invalidate(Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        caffeine.invalidateAll(keys);
        logger.atTrace()
            .addKeyValue("cacheName", cacheName)
            .addKeyValue("operation", "INVALIDATE_MANY")
            .addKeyValue("keys", keys.size())
            .log("Caffeine Cache operation completed");
    }

    @Override
    public void invalidateAll() {
        caffeine.invalidateAll();
        logger.atDebug()
            .addKeyValue("cacheName", cacheName)
            .addKeyValue("operation", "INVALIDATE_ALL")
            .log("Caffeine Cache operation completed");
    }
}

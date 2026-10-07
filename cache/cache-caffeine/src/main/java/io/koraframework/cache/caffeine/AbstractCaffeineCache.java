package io.koraframework.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetry;
import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetryFactory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetry.Operation.*;

@NullMarked
public abstract class AbstractCaffeineCache<K, V> implements CaffeineCache<K, V> {

    private final Cache<K, V> caffeine;
    private final CaffeineCacheTelemetry telemetry;
    private final boolean enabled;
    // loads in flight for computeIfAbsent, single-key and bulk: one loader per key, run outside Caffeine's atomic compute
    private final ConcurrentHashMap<K, Load<V>> loads = new ConcurrentHashMap<>();

    private record Load<V>(Thread owner, CompletableFuture<@Nullable V> result) {}

    protected AbstractCaffeineCache(String cacheConfigPath,
                                    CaffeineCacheConfig config,
                                    CaffeineCacheFactory factory,
                                    CaffeineCacheTelemetryFactory telemetryFactory) {
        this.caffeine = factory.build(cacheConfigPath, config);
        this.telemetry = telemetryFactory.get(cacheConfigPath, getClass(), config.telemetry());
        this.enabled = config.enabled();
    }

    @Override
    @Nullable
    public V get(K key) {
        if (key == null) {
            return null;
        }
        if (!enabled) {
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
        if (!enabled) {
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
        if (!enabled) {
            return Collections.emptyMap();
        }

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
        if (!enabled) {
            return mappingFunction.apply(key);
        }

        var observation = this.telemetry.observe(COMPUTE_IF_ABSENT);
        observation.observeKey(key);
        try {
            // a quiet check keeps the hit/miss stats to one record per call
            var value = caffeine.policy().getIfPresentQuietly(key) != null ? caffeine.getIfPresent(key) : null;
            if (value == null) {
                value = load(key, mappingFunction);
            }
            observation.observeValue(value);
            return value;
        } catch (Exception e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }

    /**
     * Runs the loader outside Caffeine's atomic compute, so it may call this cache again (recursive @Cacheable),
     * while concurrent callers for the same key still wait for one load, and invalidate/put during the load win.
     */
    @Nullable
    private V load(K key, Function<K, @Nullable V> mappingFunction) {
        var load = new Load<V>(Thread.currentThread(), new CompletableFuture<>());
        var inFlight = loads.putIfAbsent(key, load);
        if (inFlight != null) {
            if (inFlight.owner() == Thread.currentThread()) {
                // the same key is requested again from its own loader, waiting would deadlock
                return mappingFunction.apply(key);
            }
            return await(key, inFlight);
        }

        @SuppressWarnings("unchecked")
        var stored = (V[]) new Object[1];
        try {
            var loaded = mappingFunction.apply(key);
            stored[0] = loaded;
            // invalidate removes the in-flight load first, so a load it removed is not stored;
            // get(key, loader) records the miss and load stats and keeps a value put during the load
            loads.computeIfPresent(key, (k, l) -> {
                if (l == load) {
                    stored[0] = caffeine.get(k, kk -> loaded);
                    return null;
                }
                return l;
            });
        } catch (Throwable e) {
            // still registered: record the miss and the failed load in the stats, as get(key, loader) did
            loads.computeIfPresent(key, (k, l) -> {
                if (l == load) {
                    recordFailedLoad(k);
                    return null;
                }
                return l;
            });
            load.result().completeExceptionally(e);
            throw e;
        }
        load.result().complete(stored[0]);
        return stored[0];
    }

    /**
     * Bulk counterpart of {@link #load}: missing keys share the in-flight loads of single-key and other bulk calls,
     * and a loaded value is stored only while its key was not invalidated or put during the load.
     */
    private Map<K, V> loadAll(Collection<K> keys, Function<Set<K>, Map<K, V>> mappingFunction) {
        var unique = new LinkedHashSet<>(keys);
        // a quiet check keeps the hit/miss stats to one record per key
        var present = new ArrayList<K>();
        for (var key : unique) {
            if (caffeine.policy().getIfPresentQuietly(key) != null) {
                present.add(key);
            }
        }
        var result = new HashMap<K, V>(caffeine.getAllPresent(present));

        var own = new HashMap<K, Load<V>>();
        var waits = new HashMap<K, Load<V>>();
        var toLoad = new LinkedHashSet<K>();
        for (var key : unique) {
            if (result.containsKey(key)) {
                continue;
            }
            var load = new Load<V>(Thread.currentThread(), new CompletableFuture<>());
            var inFlight = loads.putIfAbsent(key, load);
            if (inFlight == null) {
                own.put(key, load);
                toLoad.add(key);
            } else if (inFlight.owner() == Thread.currentThread()) {
                // the key is requested again from its own loader, waiting would deadlock
                toLoad.add(key);
            } else {
                waits.put(key, inFlight);
            }
        }

        if (!toLoad.isEmpty()) {
            Map<K, V> loaded;
            try {
                loaded = mappingFunction.apply(Collections.unmodifiableSet(toLoad));
            } catch (Throwable e) {
                own.forEach((key, load) -> {
                    loads.computeIfPresent(key, (k, l) -> {
                        if (l == load) {
                            recordFailedLoad(k);
                            return null;
                        }
                        return l;
                    });
                    load.result().completeExceptionally(e);
                });
                throw e;
            }
            for (var key : toLoad) {
                var loadedValue = loaded == null ? null : loaded.get(key);
                var value = loadedValue;
                var load = own.get(key);
                if (load != null) {
                    @SuppressWarnings("unchecked")
                    var stored = (V[]) new Object[]{loadedValue};
                    // same as load(): an invalidate removed the load and wins, a value put during the load is kept
                    loads.computeIfPresent(key, (k, l) -> {
                        if (l == load) {
                            stored[0] = caffeine.get(k, kk -> loadedValue);
                            return null;
                        }
                        return l;
                    });
                    value = stored[0];
                    load.result().complete(value);
                }
                if (value != null) {
                    result.put(key, value);
                }
            }
        }

        for (var wait : waits.entrySet()) {
            var value = await(wait.getKey(), wait.getValue());
            if (value != null) {
                result.put(wait.getKey(), value);
            }
        }
        return result;
    }

    @Nullable
    private V await(K key, Load<V> inFlight) {
        try {
            return inFlight.result().join();
        } catch (CompletionException e) {
            if (e.getCause() == null) {
                throw e;
            }
            // the loader's own exception, checked ones too (Kotlin loaders can throw them), as the loading thread gets it
            throw AbstractCaffeineCache.<RuntimeException>sneakyThrow(e.getCause());
        } finally {
            // one lookup per waiting call: a hit if the loaded value is in the cache, a miss otherwise
            caffeine.getIfPresent(key);
        }
    }

    // records the miss and the failed load in the stats, as get(key, loader) did
    private void recordFailedLoad(K key) {
        try {
            caffeine.get(key, k -> {
                throw new IllegalStateException("load failed");
            });
        } catch (IllegalStateException ignored) {
            // the loader's own exception is rethrown by the caller
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    @Override
    public Map<K, V> computeIfAbsent(Collection<K> keys, Function<Set<K>, Map<K, V>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            return mappingFunction.apply(Collections.emptySet());
        }
        if (!enabled) {
            return mappingFunction.apply(Set.copyOf(keys));
        }

        var observation = this.telemetry.observe(COMPUTE_IF_ABSENT_MANY);
        observation.observeKeys(keys);
        try {
            var value = loadAll(keys, mappingFunction);
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
        if (!enabled) {
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
        if (!enabled) {
            return keyAndValues;
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
        if (!enabled) {
            return;
        }

        var observation = this.telemetry.observe(INVALIDATE);
        observation.observeKey(key);
        try {
            loads.remove(key);
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
        if (!enabled) {
            return;
        }

        var observation = this.telemetry.observe(INVALIDATE_MANY);
        observation.observeKeys(keys);
        try {
            keys.forEach(loads::remove);
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
        if (!enabled) {
            return;
        }

        var observation = this.telemetry.observe(INVALIDATE_ALL);
        try {
            observation.observeKeys(List.of());
            loads.clear();
            caffeine.invalidateAll();
        } catch (Exception e) {
            observation.observeError(e);
            throw e;
        } finally {
            observation.end();
        }
    }
}

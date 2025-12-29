package ru.tinkoff.kora.cache;

import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

final class FacadeCacheBuilder<K, V> implements Cache.Builder<K, V> {

    private final List<Cache<K, V>> facades = new ArrayList<>();

    FacadeCacheBuilder(Cache<K, V> cache) {
        facades.add(cache);
    }

    @Override
    public Cache.Builder<K, V> addCache(Cache<K, V> cache) {
        facades.add(cache);
        return this;
    }

    @Override
    public Cache<K, V> build() {
        if (facades.isEmpty()) {
            throw new IllegalArgumentException("Facades can't be empty for Facade Cache Builder!");
        }

        if (facades.size() == 1) {
            return facades.get(0);
        }

        return new FacadeCache<>(facades);
    }

    static class FacadeCache<K, V> implements Cache<K, V> {

        private final List<Cache<K, V>> facades;

        public FacadeCache(List<Cache<K, V>> facades) {
            this.facades = facades;
        }

        @Nullable
        @Override
        public V get(K key) {
            for (var facade : facades) {
                final V v = facade.get(key);
                if (v != null) {
                    return v;
                }
            }

            return null;
        }

        @Override
        public Map<K, V> get(Collection<K> keys) {
            throw new UnsupportedOperationException();
        }

        @Override
        public V put(K key, V value) {
            for (var facade : facades) {
                facade.put(key, value);
            }

            return value;
        }

        @Override
        public Map<K, V> put(Map<K, V> keyAndValues) {
            for (var facade : facades) {
                facade.put(keyAndValues);
            }

            return keyAndValues;
        }

        @Override
        public V computeIfAbsent(K key, Function<K, V> mappingFunction) {
            for (int i = 0; i < facades.size(); i++) {
                var facade = facades.get(i);
                final V v = facade.get(key);
                if (v != null) {
                    for (int j = 0; j < i; j++) {
                        var facadeToUpdate = facades.get(j);
                        facadeToUpdate.put(key, v);
                    }

                    return v;
                }
            }

            final V computed = mappingFunction.apply(key);
            for (var facade : facades) {
                facade.put(key, computed);
            }

            return computed;
        }

        @Override
        public Map<K, V> computeIfAbsent(Collection<K> keys, Function<Set<K>, Map<K, V>> mappingFunction) {
            final Map<Integer, Map<K, V>> cacheToValues = new LinkedHashMap<>();
            final Map<K, V> resultValues = new HashMap<>();
            final Set<K> keysLeft = new HashSet<>(keys);
            for (int i = 0; i < facades.size(); i++) {
                var facade = facades.get(i);
                var values = facade.get(keysLeft);

                cacheToValues.put(i, values);
                resultValues.putAll(values);
                keysLeft.removeAll(values.keySet());

                if (resultValues.size() == keys.size()) {
                    break;
                }
            }

            final Map<K, V> computed = (!keysLeft.isEmpty())
                ? mappingFunction.apply(keysLeft)
                : Collections.emptyMap();

            resultValues.putAll(computed);

            for (var missedFacade : cacheToValues.entrySet()) {
                if (missedFacade.getValue().size() != keys.size()) {
                    var facade = facades.get(missedFacade.getKey());
                    for (var rv : resultValues.entrySet()) {
                        if (!missedFacade.getValue().containsKey(rv.getKey())) {
                            facade.put(rv.getKey(), rv.getValue());
                        }
                    }
                }
            }

            return resultValues;
        }

        @Override
        public void invalidate(K key) {
            for (var facade : facades) {
                facade.invalidate(key);
            }
        }

        @Override
        public void invalidate(Collection<K> keys) {
            for (var facade : facades) {
                facade.invalidate(keys);
            }
        }

        @Override
        public void invalidateAll() {
            for (var facade : facades) {
                facade.invalidateAll();
            }
        }
    }
}

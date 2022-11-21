package ru.tinkoff.kora.cache;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class FacadeCacheManagerBuilder<K, V> implements CacheManager.Builder<K, V> {

    private final List<Facade<K, V>> facades = new ArrayList<>();

    @Nonnull
    @Override
    public CacheManager.Builder<K, V> addFacadeManager(@Nonnull CacheManager<K, V> cacheManager) {
        facades.add(new ManagerFacade<>(cacheManager));
        return this;
    }

    @Nonnull
    @Override
    public CacheManager.Builder<K, V> addFacadeFunction(@Nonnull Function<String, Cache<K, V>> cacheFunction) {
        facades.add(new FunctionFacade<>(cacheFunction));
        return this;
    }

    @Nonnull
    @Override
    public CacheManager<K, V> build() {
        if (facades.isEmpty()) {
            throw new IllegalArgumentException("Facades can't be empty for Facade Cache Manager!");
        }

        if (facades.size() == 1) {
            return new SingleFacadeCacheManager<>(facades.get(0));
        }

        return new SimpleFacadeCacheManager<>(facades);
    }

    private interface Facade<K, V> {
        Cache<K, V> getCache(@Nonnull String name);
    }

    private record ManagerFacade<K, V>(CacheManager<K, V> manager) implements Facade<K, V> {
        @Override
        public Cache<K, V> getCache(@Nonnull String name) {
            return manager.getCache(name);
        }
    }

    private record FunctionFacade<K, V>(Function<String, Cache<K, V>> function, Map<String, Cache<K, V>> cacheMap) implements Facade<K, V> {

        private FunctionFacade(Function<String, Cache<K, V>> function) {
            this(function, new HashMap<>());
        }

        @Override
        public Cache<K, V> getCache(@Nonnull String name) {
            return cacheMap.computeIfAbsent(name, function);
        }
    }

    private record FacadeCache<K, V>(String name, List<Facade<K, V>> facades) implements Cache<K, V> {

        @Nullable
        @Override
        public V get(@Nonnull K key) {
            for (Facade<K, V> facade : facades) {
                final Cache<K, V> cache = facade.getCache(name);
                final V v = cache.get(key);
                if (v != null) {
                    return v;
                }
            }

            return null;
        }

        @Nonnull
        @Override
        public Mono<V> getAsync(@Nonnull K key) {
            Mono<V> result = null;
            for (Facade<K, V> facade : facades) {
                final Cache<K, V> cache = facade.getCache(name);
                result = (result == null)
                    ? cache.getAsync(key)
                    : result.switchIfEmpty(cache.getAsync(key));
            }

            return result;
        }

        @Nonnull
        @Override
        public V put(@Nonnull K key, @Nonnull V value) {
            for (Facade<K, V> facade : facades) {
                final Cache<K, V> cache = facade.getCache(name);
                cache.put(key, value);
            }

            return value;
        }

        @Nonnull
        @Override
        public Mono<V> putAsync(@Nonnull K key, @Nonnull V value) {
            final List<Mono<V>> operations = facades.stream()
                .map(f -> f.getCache(name))
                .map(cache -> cache.putAsync(key, value))
                .toList();

            return Flux.merge(operations).then(Mono.just(value));
        }

        @Override
        public void invalidate(@Nonnull K key) {
            for (Facade<K, V> facade : facades) {
                final Cache<K, V> cache = facade.getCache(name);
                cache.invalidate(key);
            }
        }

        @Nonnull
        @Override
        public Mono<Void> invalidateAsync(@Nonnull K key) {
            final List<Mono<Void>> operations = facades.stream()
                .map(f -> f.getCache(name))
                .map(cache -> cache.invalidateAsync(key))
                .toList();

            return Flux.merge(operations).then();
        }

        @Override
        public void invalidateAll() {
            for (Facade<K, V> facade : facades) {
                final Cache<K, V> cache = facade.getCache(name);
                cache.invalidateAll();
            }
        }

        @Nonnull
        @Override
        public Mono<Void> invalidateAllAsync() {
            final List<Mono<Void>> operations = facades.stream()
                .map(f -> f.getCache(name))
                .map(Cache::invalidateAllAsync)
                .toList();

            return Flux.merge(operations).then();
        }
    }

    private record SingleFacadeCacheManager<K, V>(Facade<K, V> facade) implements CacheManager<K, V> {

        @Override
        public Cache<K, V> getCache(@Nonnull String name) {
            return facade.getCache(name);
        }
    }

    private record SimpleFacadeCacheManager<K, V>(List<Facade<K, V>> facades) implements CacheManager<K, V> {

        @Override
        public Cache<K, V> getCache(@Nonnull String name) {
            return new FacadeCache<>(name, facades);
        }
    }
}

package ru.tinkoff.kora.cache;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

final class CacheLoaderImpls {

    private CacheLoaderImpls() {}

    static final class AsyncCacheLoader<K, V> implements CacheLoader<K, V> {

        record Pair<K, V>(K key, V value) {}

        private final Function<K, Mono<V>> loader;
        private final Function<Collection<K>, Mono<Map<K, V>>> loaderMany;

        public AsyncCacheLoader(Function<K, Mono<V>> loader) {
            this.loader = loader;
            this.loaderMany = (keys) -> {
                var valuesMonos = keys.stream()
                    .map(key -> loader.apply(key)
                        .map(v -> new Pair<>(key, v)))
                    .toList();

                return Flux.merge(valuesMonos)
                    .collect(() -> new HashMap<K, V>(), (collector, value) -> collector.put(value.key(), value.value()));
            };
        }

        public AsyncCacheLoader(Function<K, Mono<V>> loader, Function<Collection<K>, Mono<Map<K, V>>> loaderMany) {
            this.loader = loader;
            this.loaderMany = loaderMany;
        }

        @Nullable
        @Override
        public V load(@Nonnull K key) {
            return loader.apply(key).block();
        }

        @Nullable
        @Override
        public Map<K, V> load(@Nonnull Collection<K> keys) {
            return loaderMany.apply(keys).block();
        }

        @Nonnull
        @Override
        public Mono<V> loadAsync(@Nonnull K key) {
            return loader.apply(key);
        }

        @Override
        public Mono<Map<K, V>> loadAsync(@Nonnull Collection<K> keys) {
            return loaderMany.apply(keys);
        }
    }

    static final class BlockingCacheLoader<K, V> implements CacheLoader<K, V> {

        private final ExecutorService executor;
        private final Function<K, V> loader;
        private final Function<Collection<K>, Map<K, V>> loaderMany;

        public BlockingCacheLoader(ExecutorService executor, Function<K, V> loader) {
            this.executor = executor;
            this.loader = loader;
            this.loaderMany = (keys) -> {
                final Map<K, V> values = new HashMap<>();
                for (K key : keys) {
                    values.put(key, loader.apply(key));
                }
                return values;
            };
        }

        public BlockingCacheLoader(ExecutorService executor, Function<K, V> loader, Function<Collection<K>, Map<K, V>> loaderMany) {
            this.executor = executor;
            this.loader = loader;
            this.loaderMany = loaderMany;
        }

        @Nullable
        @Override
        public V load(@Nonnull K key) {
            return loader.apply(key);
        }

        @Nullable
        @Override
        public Map<K, V> load(@Nonnull Collection<K> keys) {
            return loaderMany.apply(keys);
        }

        @Nonnull
        @Override
        public Mono<V> loadAsync(@Nonnull K key) {
            return Mono.create(sink -> executor.submit(() -> {
                try {
                    sink.success(loader.apply(key));
                } catch (Exception e) {
                    sink.error(e);
                }
            }));
        }

        @Override
        public Mono<Map<K, V>> loadAsync(@Nonnull Collection<K> keys) {
            return Mono.create(sink -> executor.submit(() -> {
                try {
                    sink.success(loaderMany.apply(keys));
                } catch (Exception e) {
                    sink.error(e);
                }
            }));
        }
    }

    static final class NonBlockingCacheLoader<K, V> implements CacheLoader<K, V> {

        private final Function<K, V> loader;
        private final Function<Collection<K>, Map<K, V>> loaderMany;

        public NonBlockingCacheLoader(Function<K, V> loader) {
            this.loader = loader;
            this.loaderMany = (keys) -> {
                final Map<K, V> values = new HashMap<>();
                for (K key : keys) {
                    values.put(key, loader.apply(key));
                }
                return values;
            };
        }

        public NonBlockingCacheLoader(Function<K, V> loader, Function<Collection<K>, Map<K, V>> loaderMany) {
            this.loader = loader;
            this.loaderMany = loaderMany;
        }

        @Nullable
        @Override
        public V load(@Nonnull K key) {
            return loader.apply(key);
        }

        @Nullable
        @Override
        public Map<K, V> load(@Nonnull Collection<K> keys) {
            return loaderMany.apply(keys);
        }

        @Nonnull
        @Override
        public Mono<V> loadAsync(@Nonnull K key) {
            return Mono.fromCallable(() -> loader.apply(key));
        }

        @Override
        public Mono<Map<K, V>> loadAsync(@Nonnull Collection<K> keys) {
            return Mono.fromCallable(() -> loaderMany.apply(keys));
        }
    }
}

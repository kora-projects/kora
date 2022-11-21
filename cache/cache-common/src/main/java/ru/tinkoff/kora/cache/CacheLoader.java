package ru.tinkoff.kora.cache;

import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public interface CacheLoader<K, V> {

    /**
     * Computes or retrieves the value corresponding to {@code key}.
     *
     * @param key to look value for
     * @return value associated with key
     */
    @Nullable
    V load(@Nonnull K key);

    /**
     * Computes or retrieves the value corresponding to {@code key} asynchronously.
     *
     * @param key to look value for
     * @return value associated with key or {@link Mono#empty()}
     */
    @Nonnull
    Mono<V> loadAsync(@Nonnull K key);

    class BlockingCacheLoader<K, V> implements CacheLoader<K, V> {
        private final ExecutorService executor;
        private final Function<K, V> loader;

        public BlockingCacheLoader(ExecutorService executor, Function<K, V> loader) {
            this.executor = executor;
            this.loader = loader;
        }

        @Nullable
        @Override
        public V load(@Nonnull K key) {
            return loader.apply(key);
        }

        @Nonnull
        @Override
        public Mono<V> loadAsync(@Nonnull K key) {
            return Mono.create(sink -> {
                executor.submit(() -> {
                    try {
                        sink.success(loader.apply(key));
                    } catch (Exception e) {
                        sink.error(e);
                    }
                });
            });
        }
    }

    class NonBlockingCacheLoader<K, V> implements CacheLoader<K, V> {
        private final Function<K, V> loader;

        public NonBlockingCacheLoader(Function<K, V> loader) {this.loader = loader;}

        @Nullable
        @Override
        public V load(@Nonnull K key) {
            return loader.apply(key);
        }

        @Nonnull
        @Override
        public Mono<V> loadAsync(@Nonnull K key) {
            return Mono.fromCallable(() -> loader.apply(key));
        }
    }

    class AsyncCacheLoader<K, V> implements CacheLoader<K, V> {
        private final Function<K, Mono<V>> loader;

        public AsyncCacheLoader(Function<K, Mono<V>> loader) {this.loader = loader;}

        @Nullable
        @Override
        public V load(@Nonnull K key) {
            return loader.apply(key).block();
        }

        @Nonnull
        @Override
        public Mono<V> loadAsync(@Nonnull K key) {
            return loader.apply(key);
        }
    }
}

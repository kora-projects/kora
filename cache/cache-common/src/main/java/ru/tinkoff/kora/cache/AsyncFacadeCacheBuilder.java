package ru.tinkoff.kora.cache;

import jakarta.annotation.Nonnull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

final class AsyncFacadeCacheBuilder<K, V> implements AsyncCache.Builder<K, V> {

    private final List<AsyncCache<K, V>> facades = new ArrayList<>();

    AsyncFacadeCacheBuilder(@Nonnull AsyncCache<K, V> cache) {
        facades.add(cache);
    }

    @Nonnull
    @Override
    public AsyncCache.Builder<K, V> addCache(@Nonnull AsyncCache<K, V> cache) {
        facades.add(cache);
        return this;
    }

    @Nonnull
    @Override
    public AsyncCache<K, V> build() {
        if (facades.isEmpty()) {
            throw new IllegalArgumentException("Facades can't be empty for Facade Cache Builder!");
        }

        if (facades.size() == 1) {
            return facades.get(0);
        }

        return new FacadeAsyncCache<>(facades);
    }

    private static class FacadeAsyncCache<K, V> extends FacadeCacheBuilder.FacadeCache<K, V> implements AsyncCache<K, V> {

        private final List<AsyncCache<K, V>> facades;

        private FacadeAsyncCache(List<AsyncCache<K, V>> facades) {
            super(List.copyOf(facades));
            this.facades = facades;
        }

        @Nonnull
        @Override
        public CompletionStage<V> getAsync(@Nonnull K key) {
            CompletionStage<V> result = null;
            for (var facade : facades) {
                result = (result == null)
                    ? facade.getAsync(key)
                    : result
                    .thenCompose(r -> (r != null)
                        ? CompletableFuture.completedFuture(r)
                        : facade.getAsync(key));
            }

            return result;
        }

        @Nonnull
        @Override
        public CompletionStage<Map<K, V>> getAsync(@Nonnull Collection<K> keys) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public CompletionStage<V> putAsync(@Nonnull K key, @Nonnull V value) {
            var operations = facades.stream()
                .map(cache -> cache.putAsync(key, value).toCompletableFuture())
                .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(operations).thenApply(r -> value);
        }

        @Nonnull
        @Override
        public CompletionStage<Map<K, V>> putAsync(@Nonnull Map<K, V> keyAndValues) {
            var operations = facades.stream()
                .map(cache -> cache.putAsync(keyAndValues).toCompletableFuture())
                .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(operations).thenApply(r -> keyAndValues);
        }

        @Override
        public CompletionStage<V> computeIfAbsentAsync(@Nonnull K key, @Nonnull Function<K, CompletionStage<V>> mappingFunction) {
            CompletionStage<V> result = facades.get(0).getAsync(key);
            for (int i = 1; i < facades.size(); i++) {
                final int currentFacade = i;
                var facade = facades.get(i);
                result = result.thenCompose(r -> {
                    if (r != null) {
                        return CompletableFuture.completedFuture(r);
                    }

                    return facade.getAsync(key).thenCompose(received -> {
                        final CompletableFuture<V>[] operations = new CompletableFuture[currentFacade];
                        for (int j = 0; j < currentFacade; j++) {
                            operations[j] = facades.get(j).putAsync(key, received).toCompletableFuture();
                        }
                        return CompletableFuture.allOf(
                            operations
                        ).thenApply(r2 -> received);
                    });
                });
            }

            return result.thenCompose(r -> {
                if (r != null) {
                    return CompletableFuture.completedFuture(r);
                }

                return mappingFunction.apply(key)
                    .thenCompose(received -> putAsync(key, received));
            });
        }

        @Nonnull
        @Override
        public CompletionStage<Map<K, V>> computeIfAbsentAsync(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, CompletionStage<Map<K, V>>> mappingFunction) {
            CompletionStage<Map<K, V>> result = facades.get(0).getAsync(keys);
            for (int i = 1; i < facades.size(); i++) {
                var facade = facades.get(i);
                result = result.thenCompose(r -> {
                    if (r.size() == keys.size()) {
                        return CompletableFuture.completedFuture(r);
                    }

                    final Set<K> keysLeft = keys.stream()
                        .filter(k -> !r.containsKey(k))
                        .collect(Collectors.toSet());

                    return facade.getAsync(keysLeft).thenCompose(received -> {
                        if (received.isEmpty()) {
                            return CompletableFuture.completedFuture(r);
                        } else {
                            var resultValue = new HashMap<>(received);
                            resultValue.putAll(r);
                            return putAsync(received).thenApply(r2 -> resultValue);
                        }
                    });
                });
            }

            return result.thenCompose(r -> {
                if (r.size() == keys.size()) {
                    return CompletableFuture.completedFuture(r);
                }

                final Set<K> keysLeft = keys.stream()
                    .filter(k -> !r.containsKey(k))
                    .collect(Collectors.toSet());

                return mappingFunction.apply(keysLeft).thenCompose(received -> {
                    var resultValue = new HashMap<>(received);
                    resultValue.putAll(r);

                    return (received.isEmpty())
                        ? CompletableFuture.completedFuture(resultValue)
                        : putAsync(received).thenApply(r2 -> resultValue);
                });
            });
        }

        @Nonnull
        @Override
        public CompletionStage<Boolean> invalidateAsync(@Nonnull K key) {
            var operations = facades.stream()
                .map(cache -> cache.invalidateAsync(key).toCompletableFuture())
                .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(operations).thenApply(r -> true);
        }

        @Override
        public CompletionStage<Boolean> invalidateAsync(@Nonnull Collection<K> keys) {
            var operations = facades.stream()
                .map(cache -> cache.invalidateAsync(keys).toCompletableFuture())
                .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(operations).thenApply(r -> true);
        }

        @Nonnull
        @Override
        public CompletionStage<Boolean> invalidateAllAsync() {
            var operations = facades.stream()
                .map(cache -> cache.invalidateAllAsync().toCompletableFuture())
                .toArray(CompletableFuture[]::new);

            return CompletableFuture.allOf(operations).thenApply(r -> true);
        }
    }
}

package ru.tinkoff.kora.cache;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

final class FacadeCacheBuilder<K, V> implements CacheBuilder<K, V> {

    private final List<Cache<K, V>> facades = new ArrayList<>();

    FacadeCacheBuilder(@Nonnull Cache<K, V> cache) {
        facades.add(cache);
    }

    @Nonnull
    @Override
    public CacheBuilder<K, V> addCache(@Nonnull Cache<K, V> cache) {
        facades.add(cache);
        return this;
    }

    @Nonnull
    @Override
    public Cache<K, V> build() {
        if (facades.isEmpty()) {
            throw new IllegalArgumentException("Facades can't be empty for Facade Cache Builder!");
        }

        if (facades.size() == 1) {
            return facades.get(0);
        }

        return new FacadeSyncCache<>(facades);
    }

    private record FacadeSyncCache<K, V>(List<Cache<K, V>> facades) implements Cache<K, V> {

        @Nullable
        @Override
        public V get(@Nonnull K key) {
            for (var facade : facades) {
                final V v = facade.get(key);
                if (v != null) {
                    return v;
                }
            }

            return null;
        }

        @Nonnull
        @Override
        public Map<K, V> get(@Nonnull Collection<K> keys) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public V put(@Nonnull K key, @Nonnull V value) {
            for (var facade : facades) {
                facade.put(key, value);
            }

            return value;
        }

        @Override
        public V computeIfAbsent(@Nonnull K key, @Nonnull Function<K, V> mappingFunction) {
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

        @Nonnull
        @Override
        public Map<K, V> computeIfAbsent(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, Map<K, V>> mappingFunction) {
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
        public void invalidate(@Nonnull K key) {
            for (var facade : facades) {
                facade.invalidate(key);
            }
        }

        @Override
        public void invalidate(@Nonnull Collection<K> keys) {
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

        @Nonnull
        @Override
        public Mono<V> getAsync(@Nonnull K key) {
            Mono<V> result = null;
            for (var facade : facades) {
                result = (result == null)
                    ? facade.getAsync(key)
                    : result.switchIfEmpty(facade.getAsync(key));
            }

            return result;
        }

        @Nonnull
        @Override
        public Mono<Map<K, V>> getAsync(@Nonnull Collection<K> keys) {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public Mono<V> putAsync(@Nonnull K key, @Nonnull V value) {
            final List<Mono<V>> operations = facades.stream()
                .map(cache -> cache.putAsync(key, value))
                .toList();

            return Flux.merge(operations).then(Mono.just(value));
        }

        @Override
        public Mono<V> computeIfAbsentAsync(@Nonnull K key, @Nonnull Function<K, Mono<V>> mappingFunction) {
            Mono<V> result = facades.get(0).getAsync(key);
            for (int i = 1; i < facades.size(); i++) {
                final int currentFacade = i;
                var facade = facades.get(i);
                result = result.switchIfEmpty(facade.getAsync(key)
                    .flatMap(received -> {
                        final List<Mono<V>> operations = new ArrayList<>();
                        for (int j = 0; j < currentFacade; j++) {
                            operations.add(facades.get(j).putAsync(key, received));
                        }
                        return Flux.merge(operations).then(Mono.just(received));
                    }));
            }

            return result.switchIfEmpty(mappingFunction.apply(key)
                .flatMap(computed -> putAsync(key, computed)));
        }

        record ComputeResult<K, V>(Map<K, V> result, Map<Integer, Map<K, V>> cacheToValues) {}

        @Nonnull
        @Override
        public Mono<Map<K, V>> computeIfAbsentAsync(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, Mono<Map<K, V>>> mappingFunction) {
            return Mono.defer(() -> {
                Mono<ComputeResult<K, V>> result = Mono.just(new ComputeResult<>(new HashMap<>(), new HashMap<>()));
                for (int i = 0; i < facades.size(); i++) {
                    final int currentFacade = i;
                    var facade = facades.get(i);
                    result = result.flatMap(received -> {
                        if (received.result().size() == keys.size()) {
                            return Mono.just(received);
                        }

                        final Set<K> keysLeft = keys.stream()
                            .filter(k -> !received.result().containsKey(k))
                            .collect(Collectors.toSet());

                        return facade.getAsync(keysLeft)
                            .map(receivedNow -> {
                                var cacheToValuesNow = new HashMap<>(received.cacheToValues());
                                cacheToValuesNow.put(currentFacade, receivedNow);
                                var resultNow = new HashMap<>(receivedNow);
                                resultNow.putAll(received.result());
                                return new ComputeResult<>(resultNow, cacheToValuesNow);
                            })
                            .switchIfEmpty(Mono.just(received));
                    });
                }

                return result.flatMap(received -> {
                    if (received.result().size() == keys.size()) {
                        return Mono.just(received.result());
                    }

                    final Set<K> keysLeft = keys.stream()
                        .filter(k -> !received.result().containsKey(k))
                        .collect(Collectors.toSet());

                    return mappingFunction.apply(keysLeft)
                        .switchIfEmpty(Mono.just(Collections.emptyMap()))
                        .flatMap(computed -> {
                            var resultFinal = new HashMap<>(computed);
                            resultFinal.putAll(received.result());

                            final List<Mono<V>> puts = received.cacheToValues().entrySet().stream()
                                .flatMap(e -> {
                                    var facade = facades.get(e.getKey());
                                    return resultFinal.entrySet().stream()
                                        .filter(resultEntry -> !e.getValue().containsKey(resultEntry.getKey()))
                                        .map(resultEntry -> facade.putAsync(resultEntry.getKey(), resultEntry.getValue()));
                                })
                                .toList();

                            return Flux.merge(puts).then(Mono.just(resultFinal));
                        });
                });
            });
        }

        @Nonnull
        @Override
        public Mono<Boolean> invalidateAsync(@Nonnull K key) {
            final List<Mono<Boolean>> operations = facades.stream()
                .map(cache -> cache.invalidateAsync(key))
                .toList();

            return Flux.merge(operations).reduce((v1, v2) -> v1 && v2);
        }

        @Override
        public Mono<Boolean> invalidateAsync(@Nonnull Collection<K> keys) {
            final List<Mono<Boolean>> operations = facades.stream()
                .map(cache -> cache.invalidateAsync(keys))
                .toList();

            return Flux.merge(operations).reduce((v1, v2) -> v1 && v2);
        }

        @Nonnull
        @Override
        public Mono<Boolean> invalidateAllAsync() {
            final List<Mono<Boolean>> operations = facades.stream()
                .map(Cache::invalidateAllAsync)
                .toList();

            return Flux.merge(operations).reduce((v1, v2) -> v1 && v2);
        }
    }
}

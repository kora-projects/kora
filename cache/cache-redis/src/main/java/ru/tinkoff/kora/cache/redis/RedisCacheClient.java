package ru.tinkoff.kora.cache.redis;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.application.graph.Lifecycle;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public interface RedisCacheClient extends Lifecycle {

    @Nonnull
    CompletionStage<byte[]> get(byte[] key);

    @Nonnull
    CompletionStage<Map<byte[], byte[]>> mget(byte[][] keys);

    @Nonnull
    CompletionStage<byte[]> getex(byte[] key, long expireAfterMillis);

    @Nonnull
    default CompletionStage<Map<byte[], byte[]>> getex(byte[][] keys, long expireAfterMillis) {
        final CompletableFuture[] values = Arrays.stream(keys)
            .map(k -> getex(k, expireAfterMillis)
                .thenApply(v -> Map.entry(k, v)).toCompletableFuture())
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(values)
            .thenApply(v -> Arrays.stream(values)
                .map(f -> ((Map.Entry<byte[], byte[]>) f.join()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @Nonnull
    CompletionStage<Boolean> set(byte[] key, byte[] value);

    @Nonnull
    CompletionStage<Boolean> mset(Map<byte[], byte[]> keyAndValue);

    @Nonnull
    CompletionStage<Boolean> psetex(byte[] key, byte[] value, long expireAfterMillis);

    @Nonnull
    default CompletionStage<Boolean> psetex(Map<byte[], byte[]> keyAndValue, long expireAfterMillis) {
        final CompletableFuture[] values = keyAndValue.entrySet().stream()
            .map(e -> psetex(e.getKey(), e.getValue(), expireAfterMillis).toCompletableFuture())
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(values).thenApply(v -> true);
    }

    @Nonnull
    CompletionStage<Long> del(byte[] key);

    @Nonnull
    CompletionStage<Long> del(byte[][] keys);

    @Nonnull
    CompletionStage<Boolean> flushAll();
}

package ru.tinkoff.kora.cache.redis.client;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.reactive.RedisKeyReactiveCommands;
import io.lettuce.core.api.reactive.RedisServerReactiveCommands;
import io.lettuce.core.api.reactive.RedisStringReactiveCommands;
import io.lettuce.core.api.sync.RedisKeyCommands;
import io.lettuce.core.api.sync.RedisServerCommands;
import io.lettuce.core.api.sync.RedisStringCommands;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;

public record LettuceBasicCommands(Sync sync, Reactive reactive, StatefulConnection<byte[], byte[]> connection) implements Lifecycle, AutoCloseable {

    public record Sync(RedisServerCommands<byte[], byte[]> serverCommands,
                       RedisStringCommands<byte[], byte[]> stringCommands,
                       RedisKeyCommands<byte[], byte[]> keyCommands) {}

    public record Reactive(RedisServerReactiveCommands<byte[], byte[]> serverCommands,
                           RedisStringReactiveCommands<byte[], byte[]> stringCommands,
                           RedisKeyReactiveCommands<byte[], byte[]> keyCommands) {}

    @Override
    public void close() {
        connection.close();
    }

    @Override
    public Mono<?> init() {
        return Mono.empty();
    }

    @Override
    public Mono<?> release() {
        return Mono.fromCompletionStage(connection.closeAsync());
    }
}

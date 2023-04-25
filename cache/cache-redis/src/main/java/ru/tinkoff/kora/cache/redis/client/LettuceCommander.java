package ru.tinkoff.kora.cache.redis.client;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.reactive.RedisKeyReactiveCommands;
import io.lettuce.core.api.reactive.RedisServerReactiveCommands;
import io.lettuce.core.api.reactive.RedisStringReactiveCommands;
import io.lettuce.core.api.sync.RedisKeyCommands;
import io.lettuce.core.api.sync.RedisServerCommands;
import io.lettuce.core.api.sync.RedisStringCommands;

public interface LettuceCommander {

    record Sync(RedisServerCommands<byte[], byte[]> serverCommands,
                RedisStringCommands<byte[], byte[]> stringCommands,
                RedisKeyCommands<byte[], byte[]> keyCommands) {}

    record Reactive(RedisServerReactiveCommands<byte[], byte[]> serverCommands,
                    RedisStringReactiveCommands<byte[], byte[]> stringCommands,
                    RedisKeyReactiveCommands<byte[], byte[]> keyCommands) {}

    Sync sync();

    Reactive reactive();
}

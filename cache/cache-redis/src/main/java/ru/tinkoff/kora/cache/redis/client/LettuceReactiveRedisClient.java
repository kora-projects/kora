package ru.tinkoff.kora.cache.redis.client;

import io.lettuce.core.FlushMode;
import io.lettuce.core.GetExArgs;
import io.lettuce.core.api.reactive.RedisKeyReactiveCommands;
import io.lettuce.core.api.reactive.RedisServerReactiveCommands;
import io.lettuce.core.api.reactive.RedisStringReactiveCommands;
import reactor.core.publisher.Mono;

import java.time.Duration;

public final class LettuceReactiveRedisClient implements ReactiveRedisClient {

    private final RedisStringReactiveCommands<byte[], byte[]> stringCommands;
    private final RedisServerReactiveCommands<byte[], byte[]> serverCommands;
    private final RedisKeyReactiveCommands<byte[], byte[]> keyCommands;

    LettuceReactiveRedisClient(LettuceCommander commands) {
        this.stringCommands = commands.reactive().stringCommands();
        this.serverCommands = commands.reactive().serverCommands();
        this.keyCommands = commands.reactive().keyCommands();
    }

    @Override
    public Mono<byte[]> get(byte[] key) {
        return stringCommands.get(key);
    }

    @Override
    public Mono<byte[]> getExpire(byte[] key, long expireAfterMillis) {
        return stringCommands.getex(key, GetExArgs.Builder.ex(Duration.ofMillis(expireAfterMillis)));
    }

    @Override
    public Mono<Void> set(byte[] key, byte[] value) {
        return stringCommands.set(key, value).then();
    }

    @Override
    public Mono<Void> setExpire(byte[] key, byte[] value, long expireAfterMillis) {
        return stringCommands.psetex(key, expireAfterMillis, value).then();
    }

    @Override
    public Mono<Void> del(byte[] key) {
        return keyCommands.del(key).then();
    }

    @Override
    public Mono<Void> flushAll() {
        return serverCommands.flushall(FlushMode.SYNC).then();
    }
}

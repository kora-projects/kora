package ru.tinkoff.kora.cache.redis.client;

import io.lettuce.core.FlushMode;
import io.lettuce.core.GetExArgs;
import io.lettuce.core.api.reactive.RedisKeyReactiveCommands;
import io.lettuce.core.api.reactive.RedisServerReactiveCommands;
import io.lettuce.core.api.reactive.RedisStringReactiveCommands;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

final class LettuceReactiveRedisClient implements ReactiveRedisClient {

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
    public Mono<Map<byte[], byte[]>> get(byte[][] keys) {
        return stringCommands.mget(keys).collect(HashMap::new, ((collector, keyValue) -> collector.put(keyValue.getKey(), keyValue.getValue())));
    }

    @Override
    public Mono<byte[]> getExpire(byte[] key, long expireAfterMillis) {
        return stringCommands.getex(key, GetExArgs.Builder.ex(Duration.ofMillis(expireAfterMillis)));
    }

    @Override
    public Mono<Map<byte[], byte[]>> getExpire(byte[][] key, long expireAfterMillis) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Mono<Boolean> set(byte[] key, byte[] value) {
        return stringCommands.set(key, value).then(Mono.just(true));
    }

    @Override
    public Mono<Boolean> setExpire(byte[] key, byte[] value, long expireAfterMillis) {
        return stringCommands.psetex(key, expireAfterMillis, value).then(Mono.just(true));
    }

    @Override
    public Mono<Long> del(byte[] key) {
        return keyCommands.del(key);
    }

    @Override
    public Mono<Long> del(byte[][] keys) {
        return keyCommands.del(keys);
    }

    @Override
    public Mono<Boolean> flushAll() {
        return serverCommands.flushall(FlushMode.SYNC).then(Mono.just(true));
    }
}

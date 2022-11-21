package ru.tinkoff.kora.cache.redis.client;

import io.lettuce.core.FlushMode;
import io.lettuce.core.GetExArgs;
import io.lettuce.core.api.sync.RedisKeyCommands;
import io.lettuce.core.api.sync.RedisServerCommands;
import io.lettuce.core.api.sync.RedisStringCommands;

import java.time.Duration;

public final class LettuceSyncRedisClient implements SyncRedisClient {

    private final RedisStringCommands<byte[], byte[]> stringCommands;
    private final RedisServerCommands<byte[], byte[]> serverCommands;
    private final RedisKeyCommands<byte[], byte[]> keyCommands;

    LettuceSyncRedisClient(LettuceBasicCommands commands) {
        this.stringCommands = commands.sync().stringCommands();
        this.serverCommands = commands.sync().serverCommands();
        this.keyCommands = commands.sync().keyCommands();
    }

    @Override
    public byte[] get(byte[] key) {
        return stringCommands.get(key);
    }

    @Override
    public byte[] getExpire(byte[] key, long expireAfterMillis) {
        return stringCommands.getex(key, GetExArgs.Builder.ex(Duration.ofMillis(expireAfterMillis)));
    }

    @Override
    public void set(byte[] key, byte[] value) {
        stringCommands.set(key, value);
    }

    @Override
    public void setExpire(byte[] key, byte[] value, long expireAfterMillis) {
        stringCommands.psetex(key, expireAfterMillis, value);
    }

    @Override
    public void del(byte[] key) {
        keyCommands.del(key);
    }

    @Override
    public void flushAll() {
        serverCommands.flushall(FlushMode.SYNC);
    }
}

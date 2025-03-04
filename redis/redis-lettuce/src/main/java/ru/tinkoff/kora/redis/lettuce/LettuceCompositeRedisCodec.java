package ru.tinkoff.kora.redis.lettuce;

import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.internal.LettuceAssert;

import java.nio.ByteBuffer;

final class LettuceCompositeRedisCodec<K, V> implements RedisCodec<K, V> {

    private final RedisCodec<K, ?> keyCodec;
    private final RedisCodec<?, V> valueCodec;

    LettuceCompositeRedisCodec(RedisCodec<K, ?> keyCodec, RedisCodec<?, V> valueCodec) {
        LettuceAssert.notNull(keyCodec, "Key codec must not be null");
        LettuceAssert.notNull(valueCodec, "Value codec must not be null");
        this.keyCodec = keyCodec;
        this.valueCodec = valueCodec;
    }

    @Override
    public K decodeKey(ByteBuffer bytes) {
        return keyCodec.decodeKey(bytes);
    }

    @Override
    public V decodeValue(ByteBuffer bytes) {
        return valueCodec.decodeValue(bytes);
    }

    @Override
    public ByteBuffer encodeKey(K key) {
        return keyCodec.encodeKey(key);
    }

    @Override
    public ByteBuffer encodeValue(V value) {
        return valueCodec.encodeValue(value);
    }
}

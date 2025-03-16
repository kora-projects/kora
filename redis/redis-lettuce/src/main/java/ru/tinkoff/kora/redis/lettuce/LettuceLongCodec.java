package ru.tinkoff.kora.redis.lettuce;

import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

import java.nio.ByteBuffer;

final class LettuceLongCodec implements RedisCodec<Long, Long> {

    static final RedisCodec<Long, Long> INSTANCE = new LettuceLongCodec();

    @Override
    public Long decodeKey(ByteBuffer bytes) {
        String s = StringCodec.ASCII.decodeKey(bytes);
        return s == null ? null : Long.valueOf(s);
    }

    @Override
    public Long decodeValue(ByteBuffer bytes) {
        return decodeKey(bytes);
    }

    @Override
    public ByteBuffer encodeKey(Long key) {
        return StringCodec.ASCII.encodeKey(key == null ? null : key.toString());
    }

    @Override
    public ByteBuffer encodeValue(Long value) {
        return encodeKey(value);
    }
}

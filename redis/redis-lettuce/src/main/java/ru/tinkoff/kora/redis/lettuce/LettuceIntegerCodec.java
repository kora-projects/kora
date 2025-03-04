package ru.tinkoff.kora.redis.lettuce;

import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;

import java.nio.ByteBuffer;

final class LettuceIntegerCodec implements RedisCodec<Integer, Integer> {

    static final RedisCodec<Integer, Integer> INSTANCE = new LettuceIntegerCodec();

    @Override
    public Integer decodeKey(ByteBuffer bytes) {
        String s = StringCodec.ASCII.decodeKey(bytes);
        return s == null ? null : Integer.valueOf(s);
    }

    @Override
    public Integer decodeValue(ByteBuffer bytes) {
        return decodeKey(bytes);
    }

    @Override
    public ByteBuffer encodeKey(Integer key) {
        return StringCodec.ASCII.encodeKey(key == null ? null : key.toString());
    }

    @Override
    public ByteBuffer encodeValue(Integer value) {
        return encodeKey(value);
    }
}

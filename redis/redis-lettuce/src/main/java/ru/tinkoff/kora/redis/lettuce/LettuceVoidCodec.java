package ru.tinkoff.kora.redis.lettuce;

import io.lettuce.core.codec.RedisCodec;

import java.nio.ByteBuffer;

final class LettuceVoidCodec implements RedisCodec<Void, Void> {

    static final RedisCodec<Void, Void> INSTANCE = new LettuceVoidCodec();

    @Override
    public Void decodeKey(ByteBuffer bytes) {
        return null;
    }

    @Override
    public Void decodeValue(ByteBuffer bytes) {
        return null;
    }

    @Override
    public ByteBuffer encodeKey(Void key) {
        return null;
    }

    @Override
    public ByteBuffer encodeValue(Void value) {
        return null;
    }
}

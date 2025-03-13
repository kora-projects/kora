package ru.tinkoff.kora.redis.lettuce;

import io.lettuce.core.codec.RedisCodec;

import java.nio.ByteBuffer;

final class LettuceByteBufferCodec implements RedisCodec<ByteBuffer, ByteBuffer> {

    static final RedisCodec<ByteBuffer, ByteBuffer> INSTANCE = new LettuceByteBufferCodec();

    @Override
    public ByteBuffer decodeKey(ByteBuffer bytes) {
        return copy(bytes);
    }

    @Override
    public ByteBuffer decodeValue(ByteBuffer bytes) {
        return copy(bytes);
    }

    @Override
    public ByteBuffer encodeKey(ByteBuffer key) {
        return copy(key);
    }

    @Override
    public ByteBuffer encodeValue(ByteBuffer value) {
        return copy(value);
    }

    private static ByteBuffer copy(ByteBuffer source) {
        ByteBuffer copy = ByteBuffer.allocate(source.remaining());
        copy.put(source);
        copy.flip();
        return copy;
    }
}

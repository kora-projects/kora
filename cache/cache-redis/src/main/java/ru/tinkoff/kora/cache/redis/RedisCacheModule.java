package ru.tinkoff.kora.cache.redis;

import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.cache.CacheKey;
import ru.tinkoff.kora.cache.redis.client.LettuceModule;
import ru.tinkoff.kora.cache.telemetry.CacheMetrics;
import ru.tinkoff.kora.cache.telemetry.CacheTracer;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.json.common.JsonCommonModule;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public interface RedisCacheModule extends JsonCommonModule, LettuceModule {

    @DefaultComponent
    default RedisCacheTelemetry redisCacheTelemetry(@Nullable CacheMetrics metrics, @Nullable CacheTracer tracer) {
        return new RedisCacheTelemetry(metrics, tracer);
    }

    @DefaultComponent
    default <V> RedisCacheValueMapper<V> redisValueMapper(JsonWriter<V> jsonWriter, JsonReader<V> jsonReader, TypeRef<V> valueRef) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(V value) {
                try {
                    return jsonWriter.toByteArray(value);
                } catch (IOException e) {
                    throw new IllegalStateException(e.getMessage());
                }
            }

            @Override
            public V read(byte[] serializedValue) {
                try {
                    return (serializedValue == null) ? null : jsonReader.read(serializedValue);
                } catch (IOException e) {
                    throw new IllegalStateException(e.getMessage());
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<String> stringRedisValueMapper() {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(String value) {
                return value.getBytes(StandardCharsets.UTF_8);
            }

            @Override
            public String read(byte[] serializedValue) {
                return (serializedValue == null) ? null : new String(serializedValue, StandardCharsets.UTF_8);
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<byte[]> bytesRedisValueMapper() {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(byte[] value) {
                return value;
            }

            @Override
            public byte[] read(byte[] serializedValue) {
                return serializedValue;
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<Integer> intRedisValueMapper(RedisCacheKeyMapper<Integer> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(Integer value) {
                return keyMapper.apply(value);
            }

            @Override
            public Integer read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    int result = 0;
                    for (int i = 0; i < 4; i++) {
                        result <<= 8;
                        result |= (serializedValue[i] & 0xFF);
                    }
                    return result;
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<Long> longRedisValueMapper(RedisCacheKeyMapper<Long> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(Long value) {
                return keyMapper.apply(value);
            }

            @Override
            public Long read(byte[] serializedValue) {
                if (serializedValue == null) {
                    return null;
                } else {
                    long result = 0;
                    for (int i = 0; i < 8; i++) {
                        result <<= 8;
                        result |= (serializedValue[i] & 0xFF);
                    }
                    return result;
                }
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<BigInteger> bigIntRedisValueMapper(RedisCacheKeyMapper<BigInteger> keyMapper) {
        return new RedisCacheValueMapper<>() {
            @Override
            public byte[] write(BigInteger value) {
                return keyMapper.apply(value);
            }

            @Override
            public BigInteger read(byte[] serializedValue) {
                return (serializedValue == null) ? null : new BigInteger(serializedValue);
            }
        };
    }

    @DefaultComponent
    default RedisCacheValueMapper<UUID> uuidRedisValueMapper(RedisCacheKeyMapper<UUID> keyMapper) {
        return new RedisCacheValueMapper<>() {

            @Override
            public byte[] write(UUID value) {
                return keyMapper.apply(value);
            }

            @Override
            public UUID read(byte[] serializedValue) {
                return (serializedValue == null) ? null : new UUID(serializedValue[0], serializedValue[1]);
            }
        };
    }

    // Keys
    @DefaultComponent
    default <T extends CacheKey> RedisCacheKeyMapper<T> redisKeyMapper(TypeRef<T> keyRef) {
        return c -> c.joined().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Integer> intRedisKeyMapper() {
        return c -> String.valueOf(c).getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<Long> longRedisKeyMapper() {
        return c -> String.valueOf(c).getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<BigInteger> bigIntRedisKeyMapper() {
        return c -> c.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<UUID> uuidRedisKeyMapper() {
        return c -> c.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default RedisCacheKeyMapper<String> stringRedisKeyMapper() {
        return c -> c.getBytes(StandardCharsets.UTF_8);
    }
}

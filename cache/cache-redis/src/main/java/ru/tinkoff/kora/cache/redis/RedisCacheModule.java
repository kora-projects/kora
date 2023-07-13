package ru.tinkoff.kora.cache.redis;

import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.cache.CacheKey;
import ru.tinkoff.kora.cache.CacheManager;
import ru.tinkoff.kora.cache.redis.client.LettuceModule;
import ru.tinkoff.kora.cache.redis.client.ReactiveRedisClient;
import ru.tinkoff.kora.cache.redis.client.SyncRedisClient;
import ru.tinkoff.kora.cache.telemetry.CacheMetrics;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;
import ru.tinkoff.kora.cache.telemetry.CacheTracer;
import ru.tinkoff.kora.cache.telemetry.DefaultCacheTelemetry;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.json.common.JsonCommonModule;
import ru.tinkoff.kora.json.common.JsonReader;
import ru.tinkoff.kora.json.common.JsonWriter;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public interface RedisCacheModule extends JsonCommonModule, LettuceModule {

    @Tag(RedisCacheManager.class)
    @DefaultComponent
    default CacheTelemetry defaultCacheTelemetry(@Nullable CacheMetrics metrics, @Nullable CacheTracer tracer) {
        return new DefaultCacheTelemetry(metrics, tracer);
    }

    default RedisCacheConfig redisCacheConfig(Config config, ConfigValueExtractor<RedisCacheConfig> extractor) {
        var value = config.get("cache");
        return extractor.extract(value);
    }

    default <V> RedisValueMapper<V> redisValueMapper(JsonWriter<V> jsonWriter, JsonReader<V> jsonReader, TypeRef<V> valueRef) {
        return new RedisValueMapper<>() {
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

    default <T extends CacheKey> RedisKeyMapper<T> redisKeyMapper(TypeRef<T> keyRef) {
        return c -> c.values().stream().map(String::valueOf).collect(Collectors.joining("-")).getBytes(StandardCharsets.UTF_8);
    }

    @Tag(RedisCacheManager.class)
    default <K, V> CacheManager<K, V> taggedRedisCacheManager(RedisKeyMapper<K> keyMapper,
                                                              RedisValueMapper<V> valueMapper,
                                                              SyncRedisClient syncClient,
                                                              ReactiveRedisClient reactiveClient,
                                                              RedisCacheConfig cacheConfig,
                                                              @Tag(RedisCacheManager.class) CacheTelemetry telemetry,
                                                              TypeRef<K> keyRef,
                                                              TypeRef<V> valueRef) {
        return new RedisCacheManager<>(syncClient, reactiveClient, cacheConfig, telemetry, keyMapper, valueMapper);
    }
}

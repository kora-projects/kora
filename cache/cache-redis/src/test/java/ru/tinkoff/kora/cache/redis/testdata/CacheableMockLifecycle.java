package ru.tinkoff.kora.cache.redis.testdata;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.cache.redis.client.SyncRedisClient;

public record CacheableMockLifecycle(CacheableTargetMono mono, CacheableTargetSync sync, SyncRedisClient client) implements MockLifecycle {

}

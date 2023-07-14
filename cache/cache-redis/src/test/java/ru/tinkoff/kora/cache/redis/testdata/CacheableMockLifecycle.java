package ru.tinkoff.kora.cache.redis.testdata;

import ru.tinkoff.kora.cache.redis.client.SyncRedisClient;

public record CacheableMockLifecycle(CacheableTargetMono mono, CacheableTargetSync sync, SyncRedisClient client) {

}

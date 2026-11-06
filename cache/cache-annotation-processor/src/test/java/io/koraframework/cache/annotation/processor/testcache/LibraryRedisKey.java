package io.koraframework.cache.annotation.processor.testcache;

/**
 * Key type of a library that already ships {@link $LibraryRedisKey_RedisCacheKeyMapperModule}.
 */
public record LibraryRedisKey(String tenant, String id) {}

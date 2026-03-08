package io.koraframework.cache.annotation.processor.testcache;

import io.koraframework.cache.annotation.Cache;
import io.koraframework.cache.redis.RedisCache;
import io.koraframework.json.common.annotation.Json;

import java.math.BigDecimal;

import static io.koraframework.cache.annotation.processor.testcache.DummyCacheTagged.*;

@Cache("dummy22")
public interface DummyCacheTagged extends RedisCache<@Json Key, @Json Key> {

    record Key(String k1, BigDecimal k2) {}
}

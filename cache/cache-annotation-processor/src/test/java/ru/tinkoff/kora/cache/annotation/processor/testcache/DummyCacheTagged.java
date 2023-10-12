package ru.tinkoff.kora.cache.annotation.processor.testcache;

import ru.tinkoff.kora.cache.annotation.Cache;
import ru.tinkoff.kora.cache.redis.RedisCache;
import ru.tinkoff.kora.json.common.annotation.Json;

import java.math.BigDecimal;

import static ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCacheTagged.*;

@Cache("dummy22")
public interface DummyCacheTagged extends RedisCache<@Json Key, @Json Key> {

    record Key(String k1, BigDecimal k2) {}
}

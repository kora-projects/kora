package ru.tinkoff.kora.cache.annotation.processor.testcache;

import ru.tinkoff.kora.cache.annotation.Cache;
import ru.tinkoff.kora.cache.caffeine.CaffeineCache;
import ru.tinkoff.kora.cache.redis.RedisCache;
import ru.tinkoff.kora.json.common.annotation.Json;

import java.math.BigDecimal;

import static ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCacheTagged.Key;

public interface DummyCacheInherit extends CaffeineCache<String, String>  {

    default String getValue(String arg1) {
        return get(arg1);
    }
}

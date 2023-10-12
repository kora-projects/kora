package ru.tinkoff.kora.cache.annotation.processor.testcache;

import ru.tinkoff.kora.cache.annotation.Cache;
import ru.tinkoff.kora.cache.caffeine.CaffeineCache;

import java.math.BigDecimal;

@Cache("dummy21")
public interface DummyCache21 extends CaffeineCache<DummyCache21.Key, String> {

    record Key(String k1, BigDecimal k2) {}
}

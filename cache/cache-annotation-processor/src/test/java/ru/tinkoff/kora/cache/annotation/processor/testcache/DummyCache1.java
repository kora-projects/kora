package ru.tinkoff.kora.cache.annotation.processor.testcache;

import ru.tinkoff.kora.cache.annotation.Cache;
import ru.tinkoff.kora.cache.caffeine.CaffeineCache;

@Cache("dummy")
public interface DummyCache1 extends CaffeineCache<String, String> {

}

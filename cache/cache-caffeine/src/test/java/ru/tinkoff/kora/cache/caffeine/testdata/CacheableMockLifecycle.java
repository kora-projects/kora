package ru.tinkoff.kora.cache.caffeine.testdata;

import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;

public record CacheableMockLifecycle(CacheableTargetMono mono, CacheableTargetSync sync) implements MockLifecycle {

}

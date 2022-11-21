package ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.mono

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCacheManager
import java.math.BigDecimal

class CacheableTargetGetMono {
    var value = "1"

    @Cacheable(name = "flux_cache", tags = [DummyCacheManager::class])
    fun getValue(arg1: String?, arg2: BigDecimal?): Mono<String> {
        return Mono.just(value)
    }

    @CachePut(name = "flux_cache", tags = [DummyCacheManager::class])
    fun putValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }
}

package ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.flux

import reactor.core.publisher.Flux
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache2
import java.math.BigDecimal

class CacheableGetFlux {
    var value = "1"

    @Cacheable(value = DummyCache2::class)
    fun getValue(arg1: String?, arg2: BigDecimal?): Flux<String> {
        return Flux.just(value)
    }

    @CachePut(value = DummyCache2::class)
    fun putValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }
}

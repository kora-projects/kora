package ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.flux

import reactor.core.publisher.Flux
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache21
import java.math.BigDecimal

class CacheablePutFlux {
    var value = "1"

    @Cacheable(value = DummyCache21::class)
    fun getValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }

    @CachePut(value = DummyCache21::class, parameters = ["arg1", "arg2"])
    fun putValue(arg1: String?, arg2: BigDecimal?): Flux<String> {
        return Flux.just(value)
    }
}

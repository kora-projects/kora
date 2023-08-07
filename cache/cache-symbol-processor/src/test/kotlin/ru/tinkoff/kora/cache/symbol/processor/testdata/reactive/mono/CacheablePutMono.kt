package ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.mono

import reactor.core.publisher.Mono
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache2
import java.math.BigDecimal

class CacheablePutMono {
    var value = "1"

    @Cacheable(value = DummyCache2::class)
    fun getValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }

    @CachePut(value = DummyCache2::class, parameters = ["arg1", "arg2"])
    fun putValue(arg1: String?, arg2: BigDecimal?): Mono<String> {
        return Mono.just(value)
    }
}

package ru.tinkoff.kora.cache.symbol.processor.testdata.reactive.mono

import reactor.core.publisher.Mono
import ru.tinkoff.kora.cache.annotation.CachePut
import ru.tinkoff.kora.cache.annotation.Cacheable
import ru.tinkoff.kora.cache.symbol.processor.testcache.DummyCache21
import java.math.BigDecimal

class CacheableGetMono {
    var value = "1"

    @Cacheable(value = DummyCache21::class)
    fun getValue(arg1: String?, arg2: BigDecimal?): Mono<String> {
        return Mono.just(value)
    }

    @CachePut(value = DummyCache21::class)
    fun putValue(arg1: String?, arg2: BigDecimal?): String {
        return value
    }
}

package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.`typealias`

import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.annotation.Root
import ru.tinkoff.kora.resilient.circuitbreaker.annotation.CircuitBreaker

typealias CB = CircuitBreaker

@Component
@Root
open class CircuitBreakerAliasTarget {

    var alwaysFail = true

    @CB("custom1")
    open fun getValueSync(): String {
        check(!alwaysFail) { "Failed" }
        return "OK"
    }

}

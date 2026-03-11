package io.koraframework.resilient.symbol.processor.aop.testdata.`typealias`

import io.koraframework.common.Component
import io.koraframework.common.annotation.Root
import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreaker

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

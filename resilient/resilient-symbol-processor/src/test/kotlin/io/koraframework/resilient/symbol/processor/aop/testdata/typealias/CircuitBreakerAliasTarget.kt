package io.koraframework.resilient.symbol.processor.aop.testdata.`typealias`

import io.koraframework.common.annotation.Component
import io.koraframework.common.annotation.Root
import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreaker
import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreakable

@CircuitBreaker("custom1")
interface TestCircuitBreaker : io.koraframework.resilient.circuitbreaker.CircuitBreaker

typealias CB = CircuitBreakable

@Component
@Root
open class CircuitBreakerAliasTarget {

    var alwaysFail = true

    @CB(TestCircuitBreaker::class)
    open fun getValueSync(): String {
        check(!alwaysFail) { "Failed" }
        return "OK"
    }

}

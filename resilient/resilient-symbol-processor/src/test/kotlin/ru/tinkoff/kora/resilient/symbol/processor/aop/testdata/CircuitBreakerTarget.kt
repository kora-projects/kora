package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.resilient.circuitbreaker.annotation.CircuitBreaker

@Component
open class CircuitBreakerTarget : MockLifecycle {

    var alwaysFail = true

    @CircuitBreaker("custom1")
    open fun getValueSync(): String {
        check(!alwaysFail) { "Failed" }
        return "OK"
    }

    @CircuitBreaker("custom1")
    open fun getValueSyncVoid() {
        check(!alwaysFail) { "Failed" }
    }

    @CircuitBreaker("custom2")
    open suspend fun getValueSuspend(): String {
        check(!alwaysFail) { "Failed" }
        return "OK"
    }

    @CircuitBreaker("custom3")
    open fun getValueFLow(): Flow<String> {
        check(!alwaysFail) { "Failed" }
        return flow {
            emit("OK")
        }
    }
}

package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.resilient.circuitbreaker.annotation.CircuitBreaker

@Component
open class CircuitBreakerFallbackTarget : MockLifecycle {

    companion object {
        val VALUE = "OK"
        val FALLBACK = "FALLBACK"
    }

    var alwaysFail = true

    @CircuitBreaker("custom_fallback1", fallbackMethod = "getFallbackSync")
    open fun getValueSync(): String {
        check(!alwaysFail) { "Failed" }
        return VALUE
    }

    protected fun getFallbackSync(): String {
        return FALLBACK
    }

    @CircuitBreaker("custom_fallback2", fallbackMethod = "getFallbackSuspend")
    open suspend fun getValueSuspend(): String {
        check(!alwaysFail) { "Failed" }
        return "OK"
    }

    suspend fun getFallbackSuspend(): String {
        return FALLBACK;
    }

    @CircuitBreaker("custom_fallback3", fallbackMethod = "getFallbackFlow")
    open fun getValueFLow(): Flow<String> {
        check(!alwaysFail) { "Failed" }
        return flow {
            emit("OK")
        }
    }

    fun getFallbackFlow(): Flow<String> {
        return flow {
            emit(FALLBACK)
        }
    }
}

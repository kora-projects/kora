package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.resilient.fallback.annotation.Fallback

@Component
open class FallbackTarget : MockLifecycle {

    companion object {
        val VALUE = "OK"
        val FALLBACK = "FALLBACK"
    }

    enum class VoidState {
        NONE,
        VALUE,
        FALLBACK
    }

    var alwaysFail = true
    var voidState: VoidState = VoidState.NONE

    @Fallback("custom_fallback1", method = "getFallbackVoidSync()")
    open fun voidSync() {
        check(!alwaysFail) { "Failed" }
        voidState = VoidState.VALUE
    }

    protected fun getFallbackVoidSync() {
        voidState = VoidState.FALLBACK
    }

    @Fallback("custom_fallback1", method = "getFallbackSync()")
    open fun getValueSync(): String {
        check(!alwaysFail) { "Failed" }
        return VALUE
    }

    protected fun getFallbackSync(): String {
        return FALLBACK
    }

    @Fallback("custom_fallback2", method = "getFallbackSuspend()")
    open suspend fun getValueSuspend(): String {
        check(!alwaysFail) { "Failed" }
        return "OK"
    }

    suspend fun getFallbackSuspend(): String {
        return FALLBACK;
    }

    @Fallback("custom_fallback3", method = "getFallbackFlow()")
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

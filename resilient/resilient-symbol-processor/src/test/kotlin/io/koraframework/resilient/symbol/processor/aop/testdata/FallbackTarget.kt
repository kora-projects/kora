package io.koraframework.resilient.symbol.processor.aop.testdata

import io.koraframework.common.Component
import io.koraframework.common.annotation.Root
import io.koraframework.resilient.fallback.annotation.Fallback

@Component
@Root
open class FallbackTarget {

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

    // Throws here is an alias for kotlin.jvm.Throws
    // Method should compile normally
    @Throws(IllegalStateException::class)
    @Fallback("custom_Throws", method = "getFallbackVoidSync()")
    open fun throws() {
        check(!alwaysFail) { "Failed" }
        voidState = VoidState.VALUE
    }
}

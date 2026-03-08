package io.koraframework.resilient.symbol.processor.aop.testdata

import io.koraframework.common.Component
import io.koraframework.resilient.fallback.annotation.Fallback

@Component
open class FallbackIllegalArgumentTarget {

    companion object {
        const val VALUE = "OK"
        const val FALLBACK = "FALLBACK"
    }

    var alwaysFail = true

    @Fallback("custom_fallback1", method = "getFallbackSync(arg3)")
    open fun getValueSync(arg1: String, arg2: String): String {
        check(!alwaysFail) { "Failed" }
        return VALUE
    }

    protected fun getFallbackSync(arg1: String, arg2: String): String {
        return FALLBACK + arg1 + arg2
    }
}

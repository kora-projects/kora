package io.koraframework.resilient.symbol.processor.aop.testdata.`typealias`

import io.koraframework.common.Component
import io.koraframework.common.annotation.Root
import io.koraframework.resilient.fallback.annotation.Fallback

typealias FallbackAlias = Fallback

@Component
@Root
open class FallbackAliasTarget {

    companion object {
        const val VALUE = "OK"
        const val FALLBACK = "FALLBACK"
    }

    var alwaysFail = true

    @FallbackAlias("custom_fallback1", method = "getFallbackSync()")
    open fun getValueSync(): String {
        check(!alwaysFail) { "Failed" }
        return VALUE
    }

    protected fun getFallbackSync(): String {
        return FALLBACK
    }

}

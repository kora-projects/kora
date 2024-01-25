package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.`typealias`

import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.annotation.Root
import ru.tinkoff.kora.resilient.fallback.annotation.Fallback

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

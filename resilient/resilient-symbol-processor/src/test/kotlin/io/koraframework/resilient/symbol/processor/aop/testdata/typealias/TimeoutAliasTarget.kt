package io.koraframework.resilient.symbol.processor.aop.testdata.`typealias`


import io.koraframework.common.Component
import io.koraframework.common.annotation.Root
import io.koraframework.resilient.timeout.annotation.Timeout

typealias TimeoutAlias = Timeout

@Component
@Root
open class TimeoutAliasTarget {

    @TimeoutAlias("custom1")
    open fun getValueSync(): String {
        Thread.sleep(2000)
        return "OK"
    }
}

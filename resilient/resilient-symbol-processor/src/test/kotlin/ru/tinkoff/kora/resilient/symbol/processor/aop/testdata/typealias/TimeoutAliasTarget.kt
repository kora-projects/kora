package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.`typealias`


import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.annotation.Root
import ru.tinkoff.kora.resilient.timeout.annotation.Timeout

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

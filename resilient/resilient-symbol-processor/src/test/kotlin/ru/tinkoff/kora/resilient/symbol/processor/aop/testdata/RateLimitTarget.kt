package ru.tinkoff.kora.resilient.symbol.processor.aop.testdata

import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.annotation.Root
import ru.tinkoff.kora.resilient.ratelimiter.annotation.RateLimit

@Component
@Root
open class RateLimitTarget {

    @RateLimit("custom1")
    open fun getValueSync(): String {
        return "OK"
    }

    @RateLimit("custom1")
    open fun getValueSyncVoid() {
        // no-op
    }
}

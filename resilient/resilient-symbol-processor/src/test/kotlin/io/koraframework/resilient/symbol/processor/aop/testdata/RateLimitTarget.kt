package io.koraframework.resilient.symbol.processor.aop.testdata

import io.koraframework.common.Component
import io.koraframework.common.annotation.Root
import io.koraframework.resilient.ratelimiter.annotation.RateLimit

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

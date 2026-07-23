package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import io.koraframework.resilient.ratelimiter.exception.RateLimitExceededException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@KspExperimental
class RateLimitTests : ResilientAopSymbolTestSupport() {

    @Test
    fun syncRateLimitFirstCallSucceeds() {
        val service = compileRateLimitTarget("""
            @RateLimited(TestRateLimiter::class)
            open fun call(): String = "OK"
        """)

        assertEquals("OK", call(service, "call"))
    }

    @Test
    fun syncRateLimitSecondCallExceedsLimit() {
        val service = compileRateLimitTarget("""
            @RateLimited(TestRateLimiter::class)
            open fun call(): String = "OK"
        """)

        call(service, "call")

        assertThrows<RateLimitExceededException> { call(service, "call") }
    }

    @Test
    fun voidRateLimitFirstCallSucceeds() {
        val service = compileRateLimitTarget("""
            @RateLimited(TestRateLimiter::class)
            open fun call() {}
        """)

        call(service, "call")
    }

    @Test
    fun voidRateLimitSecondCallExceedsLimit() {
        val service = compileRateLimitTarget("""
            @RateLimited(TestRateLimiter::class)
            open fun call() {}
        """)

        call(service, "call")

        assertThrows<RateLimitExceededException> { call(service, "call") }
    }

    private fun compileRateLimitTarget(method: String): Any {
        return compileApp("""
            custom1 {
              limitForPeriod = 1
              limitRefreshPeriod = 1s
            }
        """, """
            @RateLimiterSpec("custom1")
            interface TestRateLimiter : io.koraframework.resilient.ratelimiter.RateLimiter
        """, """
            @Component
            @Root
            open class TestTarget {
                $method
            }
        """)
    }
}

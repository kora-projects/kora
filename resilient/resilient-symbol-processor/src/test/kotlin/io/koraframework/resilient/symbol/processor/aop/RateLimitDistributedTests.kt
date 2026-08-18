package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import io.koraframework.resilient.ratelimiter.exception.RateLimitExceededException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@KspExperimental
class RateLimitDistributedTests : ResilientAopSymbolTestSupport() {

    @Test
    fun distributedRateLimitFirstCallSucceeds() {
        val service = compileDistributedRateLimitTarget("""
            @RateLimited(TestRateLimiter::class)
            open fun call(): String = "OK"
        """)

        assertEquals("OK", call(service, "call"))
    }

    @Test
    fun distributedRateLimitSecondCallExceedsLimit() {
        val service = compileDistributedRateLimitTarget("""
            @RateLimited(TestRateLimiter::class)
            open fun call(): String = "OK"
        """)

        call(service, "call")

        assertThrows<RateLimitExceededException> { call(service, "call") }
    }

    private fun compileDistributedRateLimitTarget(method: String): Any {
        return compileApp("""
            custom1 {
              limitForPeriod = 1
              limitRefreshPeriod = 10s
              keyPrefix = "test"
              algorithm = "FIXED_WINDOW"
            }
        """, """
            @io.koraframework.resilient.distributed.ratelimiter.annotation.RateLimiterDistributedSpec("custom1")
            interface TestRateLimiter : io.koraframework.resilient.ratelimiter.RateLimiter
        """, """
            @Component
            @Root
            open class TestTarget {
                $method
            }

            @Component
            class FakeDistributedRateLimiterClient : io.koraframework.resilient.distributed.ratelimiter.DistributedRateLimiterClient {
                private val store = java.util.concurrent.ConcurrentHashMap<String, Long>()

                override fun incrementAndExpire(key: String, ttlMillis: Long): Long = store.merge(key, 1L) { a, b -> a + b }!!

                override fun addAndExpire(key: String, delta: Long, ttlMillis: Long): Long = store.merge(key, delta) { a, b -> a + b }!!

                override fun set(key: String, value: Long, ttlMillis: Long) {
                    store[key] = value
                }
            }
        """)
    }
}

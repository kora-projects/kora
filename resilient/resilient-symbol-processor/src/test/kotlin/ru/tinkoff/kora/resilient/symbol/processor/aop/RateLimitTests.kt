package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import ru.tinkoff.kora.resilient.ratelimiter.RateLimitExceededException
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.AppWithConfig
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.CircuitBreakerTarget
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.RateLimitTarget

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class RateLimitTests : AppRunner() {

    private inline fun <reified T> getService(): T {
        val graph = getGraphForApp(
            AppWithConfig::class,
            listOf(
                CircuitBreakerTarget::class,
                RateLimitTarget::class,
            )
        )
        return getServiceFromGraph(graph)
    }

    @Test
    fun syncRateLimitFirstCallSucceeds() {
        // given
        val service = getService<RateLimitTarget>()

        // when/then — first call within limitForPeriod=1 should succeed
        val result = service.getValueSync()
        assertNotNull(result)
        assertEquals("OK", result)
    }

    @Test
    fun syncRateLimitSecondCallExceedsLimit() {
        // given
        val service = getService<RateLimitTarget>()

        // when
        service.getValueSync()

        // then — second call in the same period should be rejected
        assertThrows<RateLimitExceededException> {
            service.getValueSync()
        }
    }

    @Test
    fun voidRateLimitFirstCallSucceeds() {
        // given
        val service = getService<RateLimitTarget>()

        // when/then — should not throw
        try {
            service.getValueSyncVoid()
        } catch (e: Throwable) {
            fail("First call should not throw, but got: $e")
        }
    }

    @Test
    fun voidRateLimitSecondCallExceedsLimit() {
        // given
        val service = getService<RateLimitTarget>()

        // when
        service.getValueSyncVoid()

        // then
        assertThrows<RateLimitExceededException> {
            service.getValueSyncVoid()
        }
    }
}

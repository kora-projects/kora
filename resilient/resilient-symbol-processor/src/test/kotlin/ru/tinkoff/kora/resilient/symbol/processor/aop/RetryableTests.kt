package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.resilient.retry.RetryAttemptException
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class RetryableTests : AppRunner() {

    private inline fun <reified T> getService(): T {
        val graph = getGraphForApp(
            AppWithConfig::class,
            listOf(
                CircuitBreakerTarget::class,
                FallbackTarget::class,
                RetryableTarget::class,
                TimeoutTarget::class,
            )
        )

        return getServiceFromGraph(graph)
    }

    private val RETRY_SUCCESS = 1
    private val RETRY_FAIL = 5

    private val retryableTarget = getService<RetryableTarget>()

    @BeforeEach
    fun setup() {
        retryableTarget.reset()
    }

    @Test
    fun syncVoidRetrySuccess() {
        // given
        val service = retryableTarget

        // then
        service.setRetryAttempts(RETRY_SUCCESS)

        // then
        service.retrySyncVoid("1")
    }

    @Test
    fun syncVoidRetryFail() {
        // given
        val service = retryableTarget

        // then
        service.setRetryAttempts(RETRY_FAIL)

        // then
        try {
            service.retrySyncVoid("1")
            fail("Should not happen")
        } catch (ex: RetryAttemptException) {
            assertNotNull(ex.message)
        }
    }

    @Test
    fun syncRetrySuccess() {
        // given
        val service = retryableTarget

        // then
        service.setRetryAttempts(RETRY_SUCCESS)

        // then
        assertEquals("1", service.retrySync("1"))
    }

    @Test
    fun syncRetryFail() {
        // given
        val service = retryableTarget

        // then
        service.setRetryAttempts(RETRY_FAIL)

        // then
        try {
            service.retrySync("1")
            fail("Should not happen")
        } catch (ex: RetryAttemptException) {
            assertNotNull(ex.message)
        }
    }

    @Test
    fun suspendRetrySuccess() {
        // given
        val service = retryableTarget

        // then
        service.setRetryAttempts(RETRY_SUCCESS)

        // then
        assertEquals("1", runBlocking { service.retrySuspend("1") })
    }

    @Test
    fun suspendRetryFail() {
        // given
        val service = retryableTarget

        // then
        service.setRetryAttempts(RETRY_FAIL)

        // then
        try {
            runBlocking { service.retrySuspend("1") }
            fail("Should not happen")
        } catch (e: RetryAttemptException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun flowRetrySuccess() {
        // given
        val service = retryableTarget

        // then
        service.setRetryAttempts(RETRY_SUCCESS)

        // then
        assertEquals("1", runBlocking { service.retryFlow("1").first() })
    }

    @Test
    fun flowRetryFail() {
        // given
        val service = retryableTarget

        // then
        service.setRetryAttempts(RETRY_FAIL)

        // then
        try {
            runBlocking { service.retryFlow("1").first() }
            fail("Should not happen")
        } catch (e: RetryAttemptException) {
            assertNotNull(e.message)
        }
    }
}

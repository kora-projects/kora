package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import ru.tinkoff.kora.resilient.retry.RetryExhaustedException
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.AppWithConfig
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.RetryTarget
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.`typealias`.RetryAliasTarget

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class RetryTests : AppRunner() {

    private inline fun <reified T> getService(): T {
        val graph = getGraphForApp(
            AppWithConfig::class,
            listOf(
                RetryTarget::class,
                RetryAliasTarget::class,
            )
        )

        return getServiceFromGraph(graph)
    }

    private val EXEC_SUCCESS = 0
    private val EXEC_FAIL = 3

    private val retryTarget = getService<RetryTarget>()

    @Test
    fun syncVoidRetrySuccess() {
        // given
        val service = retryTarget

        // then
        service.setFailAttempts(EXEC_SUCCESS)

        // then
        service.retrySyncVoid("1")
        assertEquals(0, service.getRetryAttempts())
    }

    @Test
    fun syncVoidRetryFail() {
        // given
        val service = retryTarget

        // then
        service.setFailAttempts(EXEC_FAIL)

        // then
        try {
            service.retrySyncVoid("1")
            fail("Should not happen")
        } catch (ex: RetryExhaustedException) {
            assertNotNull(ex.message)
            assertEquals(2, service.getRetryAttempts())
        }
    }

    @Test
    fun syncRetrySuccess() {
        // given
        val service = retryTarget

        // then
        service.setFailAttempts(EXEC_SUCCESS)

        // then
        assertEquals("1", service.retrySync("1"))
        assertEquals(0, service.getRetryAttempts())
    }

    @Test
    fun syncRetryFail() {
        // given
        val service = retryTarget

        // then
        service.setFailAttempts(EXEC_FAIL)

        // then
        try {
            service.retrySync("1")
            fail("Should not happen")
        } catch (ex: RetryExhaustedException) {
            assertNotNull(ex.message)
            assertEquals(2, service.getRetryAttempts())
        }
    }

    @Test
    fun syncRetryZeroSuccess() {
        // given
        val service = retryTarget

        // then
        service.setFailAttempts(EXEC_SUCCESS)

        // then
        assertEquals("1", service.retrySyncZeroAttempt("1"))
        assertEquals(0, service.getRetryAttempts())
    }

    @Test
    fun syncRetryZeroFail() {
        // given
        val service = retryTarget

        // then
        service.setFailAttempts(EXEC_FAIL)

        // then
        assertThrows(IllegalStateException::class.java) { service.retrySyncZeroAttempt("1") }
        assertEquals(0, service.getRetryAttempts())
    }

    @Test
    fun syncRetryDisabledSuccess() {
        // given
        val service = retryTarget

        // then
        service.setFailAttempts(EXEC_SUCCESS)

        // then
        assertEquals("1", service.retrySyncDisabled("1"))
        assertEquals(0, service.getRetryAttempts())
    }

    @Test
    fun syncRetryDisabledFail() {
        // given
        val service = retryTarget

        // then
        service.setFailAttempts(EXEC_FAIL)

        // then
        assertThrows(IllegalStateException::class.java) { service.retrySyncDisabled("1") }
        assertEquals(0, service.getRetryAttempts())
    }

    @Test
    fun aliasAnnotationSuccess() {
        // given
        val service = getService<RetryAliasTarget>()

        // when
        service.setFailAttempts(EXEC_SUCCESS)

        // then
        assertEquals("1", service.retrySync("1"))
        assertEquals(0, service.getRetryAttempts())
    }

    @Test
    fun aliasAnnotationFail() {
        // given
        val service = getService<RetryAliasTarget>()

        // when
        service.setFailAttempts(EXEC_FAIL)

        // then
        assertThrows<RetryExhaustedException> {
            service.retrySync("1")
        }
        assertEquals(2, service.getRetryAttempts())
    }
}

package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import io.koraframework.resilient.retry.exception.RetryExhaustedException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@KspExperimental
class RetryTests : ResilientAopSymbolTestSupport() {

    @Test
    fun syncVoidRetrySuccess() {
        val service = compileRetryTarget("""
            @Retryable(TestRetry::class)
            open fun call() {
                attempts++
            }
        """)

        call(service, "call")

        assertEquals(1, call(service, "attempts"))
    }

    @Test
    fun syncVoidRetryFail() {
        val service = compileRetryTarget("""
            @Retryable(TestRetry::class)
            open fun call() {
                attempts++
                throw IllegalStateException("Failed")
            }
        """)

        val ex = assertThrows<RetryExhaustedException> { call(service, "call") }

        assertNotNull(ex.message)
        assertEquals(3, call(service, "attempts"))
    }

    @Test
    fun syncRetrySuccess() {
        val service = compileRetryTarget("""
            @Retryable(TestRetry::class)
            open fun call(value: String): String {
                attempts++
                return value
            }
        """)

        assertEquals("1", call(service, "call", "1"))
        assertEquals(1, call(service, "attempts"))
    }

    @Test
    fun syncRetryFail() {
        val service = compileRetryTarget("""
            @Retryable(TestRetry::class)
            open fun call(value: String): String {
                attempts++
                throw IllegalStateException("Failed")
            }
        """)

        val ex = assertThrows<RetryExhaustedException> { call(service, "call", "1") }

        assertNotNull(ex.message)
        assertEquals(3, call(service, "attempts"))
    }

    @Test
    fun syncRetryZeroSuccess() {
        val service = compileRetryTarget("TestRetryZeroAttempts", """
            @Retryable(TestRetryZeroAttempts::class)
            open fun call(value: String): String {
                attempts++
                return value
            }
        """)

        assertEquals("1", call(service, "call", "1"))
        assertEquals(1, call(service, "attempts"))
    }

    @Test
    fun syncRetryZeroFail() {
        val service = compileRetryTarget("TestRetryZeroAttempts", """
            @Retryable(TestRetryZeroAttempts::class)
            open fun call(value: String): String {
                attempts++
                throw IllegalStateException("Failed")
            }
        """)

        assertThrows<IllegalStateException> { call(service, "call", "1") }
        assertEquals(1, call(service, "attempts"))
    }

    @Test
    fun syncRetryDisabledSuccess() {
        val service = compileRetryTarget("TestRetryDisabled", """
            @Retryable(TestRetryDisabled::class)
            open fun call(value: String): String {
                attempts++
                return value
            }
        """)

        assertEquals("1", call(service, "call", "1"))
        assertEquals(1, call(service, "attempts"))
    }

    @Test
    fun syncRetryDisabledFail() {
        val service = compileRetryTarget("TestRetryDisabled", """
            @Retryable(TestRetryDisabled::class)
            open fun call(value: String): String {
                attempts++
                throw IllegalStateException("Failed")
            }
        """)

        assertThrows<IllegalStateException> { call(service, "call", "1") }
        assertEquals(1, call(service, "attempts"))
    }

    @Test
    fun typealiasRetrySuccess() {
        val service = compileRetryTarget("""
            typealias RetryAlias = Retryable

            @RetryAlias(TestRetry::class)
            open fun call(value: String): String {
                attempts++
                return value
            }
        """)

        assertEquals("1", call(service, "call", "1"))
        assertEquals(1, call(service, "attempts"))
    }

    private fun compileRetryTarget(method: String): Any = compileRetryTarget("TestRetry", method)

    private fun compileRetryTarget(retryType: String, method: String): Any {
        return compileApp(retryConfig(), retryInterface(retryType), """
            @Component
            @Root
            open class TestTarget {
                var attempts: Int = 0
                fun attempts(): Int = attempts
                $method
            }
        """)
    }

    private fun retryInterface(retryType: String): String {
        val configPath = when (retryType) {
            "TestRetryZeroAttempts" -> "customZeroAttempts"
            "TestRetryDisabled" -> "customDisabled"
            else -> "custom1"
        }
        return """
            @RetrySpec("$configPath")
            interface $retryType : io.koraframework.resilient.retry.Retry
        """
    }

    private fun retryConfig(): String {
        return """
            custom1 {
              delay = 1ms
              attempts = 2
            }
            customZeroAttempts {
              delay = 1ms
              attempts = 0
            }
            customDisabled {
              enabled = false
              delay = 1ms
              attempts = 2
            }
        """
    }
}

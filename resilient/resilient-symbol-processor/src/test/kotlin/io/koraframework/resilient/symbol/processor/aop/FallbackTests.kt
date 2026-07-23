package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@KspExperimental
class FallbackTests : ResilientAopSymbolTestSupport() {

    @Test
    fun incorrectArgumentFallback() {
        compileFailed("""
            class TestTarget {
                @Fallback(method = "fallback(missing)")
                open fun call(value: String): String = value
                fun fallback(value: String): String = value
            }
        """)

        compileResult.assertFailure()
    }

    @Test
    fun incorrectSignatureFallback() {
        compileFailed("""
            class TestTarget {
                @Fallback(method = "fallback(value)")
                open fun call(value: String): String = value
                fun fallback(value: String, unexpected: String): String = value
            }
        """)

        compileResult.assertFailure()
    }

    @Test
    fun syncFallback() {
        val service = compileFallbackTarget("""
            @Fallback(method = "fallback()")
            open fun call(): String {
                if (alwaysFail) {
                    throw IllegalStateException("Failed")
                }
                return "value"
            }
            fun fallback(): String = "fallback"
        """)

        setAlwaysFail(service, false)
        assertEquals("value", call(service, "call"))
        setAlwaysFail(service, true)
        assertEquals("fallback", call(service, "call"))
    }

    @Test
    fun voidFallback() {
        val service = compileFallbackTarget("""
            @Fallback(method = "fallback()")
            open fun call() {
                state = "value"
                if (alwaysFail) {
                    throw IllegalStateException("Failed")
                }
            }
            fun fallback() {
                state = "fallback"
            }
            fun state(): String = state
        """)

        setAlwaysFail(service, false)
        call(service, "call")
        assertEquals("value", call(service, "state"))
        setAlwaysFail(service, true)
        call(service, "call")
        assertEquals("fallback", call(service, "state"))
    }

    @Test
    fun typealiasFallback() {
        val service = compileFallbackTarget("""
            typealias FallbackAlias = Fallback

            @FallbackAlias(method = "fallback()")
            open fun call(): String {
                throw IllegalStateException("Failed")
            }
            fun fallback(): String = "fallback"
        """)

        assertEquals("fallback", call(service, "call"))
    }

    @Test
    fun runtimeExceptionReasonIsPassedToFallback() {
        val service = compileFallbackTarget("""
            @Fallback(method = "fallback()")
            open fun call(): String {
                throw IllegalArgumentException("reason-message")
            }
            fun fallback(@Fallback.Reason reason: RuntimeException): String {
                return reason.javaClass.simpleName + ":" + reason.message
            }
        """)

        assertEquals("IllegalArgumentException:reason-message", call(service, "call"))
    }

    @Test
    fun checkedExceptionReasonIsPassedToFallback() {
        val service = compileFallbackTarget("""
            @Throws(IOException::class)
            @Fallback(method = "fallback()")
            open fun call(): String {
                throw IOException("checked-message")
            }
            fun fallback(@Fallback.Reason reason: Exception): String {
                return reason.javaClass.simpleName + ":" + reason.message
            }
        """)

        assertEquals("IOException:checked-message", call(service, "call"))
    }

    @Test
    fun throwableReasonIsPassedToFallback() {
        val service = compileFallbackTarget("""
            @Throws(Throwable::class)
            @Fallback(method = "fallback()")
            open fun call(): String {
                throw Error("throwable-message")
            }
            fun fallback(@Fallback.Reason reason: Throwable): String {
                return reason.javaClass.simpleName + ":" + reason.message
            }
        """)

        assertEquals("Error:throwable-message", call(service, "call"))
    }

    private fun compileFallbackTarget(methods: String): Any {
        return compileApp("", """
            interface TestFallbackMarker
        """, """
            @Component
            @Root
            open class TestTarget {
                var alwaysFail: Boolean = false
                var state: String = ""
                $methods
            }
        """)
    }

    private fun setAlwaysFail(service: Any, value: Boolean) {
        var type: Class<*>? = service.javaClass
        while (type != null) {
            try {
                type.getDeclaredField("alwaysFail").also { it.isAccessible = true }.setBoolean(service, value)
                return
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        throw NoSuchFieldException("alwaysFail")
    }
}

package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import io.koraframework.resilient.timeout.exception.TimeoutExhaustedException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@KspExperimental
class TimeoutTests : ResilientAopSymbolTestSupport() {

    @Test
    fun syncTimeout() {
        val service = compileTimeoutTarget("""
            @Timeout(TestTimeout::class)
            open fun call(): String {
                Thread.sleep(100)
                return "OK"
            }
        """)

        assertThrows<TimeoutExhaustedException> { call(service, "call") }
    }

    @Test
    fun voidTimeout() {
        val service = compileTimeoutTarget("""
            @Timeout(TestTimeout::class)
            open fun call() {
                Thread.sleep(100)
            }
        """)

        assertThrows<TimeoutExhaustedException> { call(service, "call") }
    }

    @Test
    fun typealiasTimeout() {
        val service = compileTimeoutTarget("""
            typealias TimeoutAlias = Timeout

            @TimeoutAlias(TestTimeout::class)
            open fun call(): String {
                Thread.sleep(100)
                return "OK"
            }
        """)

        assertThrows<TimeoutExhaustedException> { call(service, "call") }
    }

    private fun compileTimeoutTarget(method: String): Any {
        return compileApp("""
            custom1 {
              duration = 10ms
            }
        """, """
            @TimeoutSpec("custom1")
            interface TestTimeout : io.koraframework.resilient.timeout.Timeouter
        """, """
            @Component
            @Root
            open class TestTarget {
                $method
            }
        """)
    }
}

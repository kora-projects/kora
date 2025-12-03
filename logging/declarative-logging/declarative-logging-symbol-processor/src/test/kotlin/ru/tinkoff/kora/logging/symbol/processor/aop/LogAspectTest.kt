package ru.tinkoff.kora.logging.symbol.processor.aop

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.slf4j.event.Level
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import java.util.*

class LogAspectTest : AbstractLogAspectTest() {

    @Test
    fun testLogPrintsInAndOut() {
        val aopProxy = compile(
            """
            open class Target {
                @Log
                open fun test() {}
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        aopProxy.invoke<Any>("test")

        val o = Mockito.inOrder(log)
        o.verify(log).info(">")
        o.verify(log).info("<")
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogPrintsInAndOutWhenExceptionInWarn() {
        val aopProxy = compile(
            """
             open class Target {
                @Log
                open fun test() {
                    throw RuntimeException("OPS")
                }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        val o = Mockito.inOrder(log)
        reset(log, Level.WARN)

        assertThrows<RuntimeException> { aopProxy.invoke<Any>("test") }

        o.verify(log).info(">")
        o.verify(log).warn(outData.capture(), ArgumentMatchers.eq("<"))
        verifyOutData(
            mapOf(
                "errorType" to "java.lang.RuntimeException",
                "errorMessage" to "OPS"
            )
        )
        o.verifyNoMoreInteractions()
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogPrintsInAndOutWhenExceptionInDebug() {
        val aopProxy = compile(
            """
            open class Target {
                @Log
                open fun test() {
                    throw RuntimeException("OPS")
                }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        val o = Mockito.inOrder(log)
        reset(log, Level.DEBUG)

        assertThrows<RuntimeException> { aopProxy.invoke<Any>("test") }

        o.verify(log).info(">")
        o.verify(log).warn(outData.capture(), ArgumentMatchers.eq("<"), ArgumentMatchers.any(Throwable::class.java))
        verifyOutData(
            mapOf(
                "errorType" to "java.lang.RuntimeException",
                "errorMessage" to "OPS"
            )
        )
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogPrintsOutWhenExceptionInWarn() {
        val aopProxy = compile(
            """
             open class Target {
                @Log.out
                open fun test(): String {
                    throw RuntimeException("OPS")
                }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        val o = Mockito.inOrder(log)
        reset(log, Level.WARN)

        assertThrows<RuntimeException> { aopProxy.invoke<Any>("test") }

        o.verify(log).warn(outData.capture(), ArgumentMatchers.eq("<"))
        verifyOutData(
            mapOf(
                "errorType" to "java.lang.RuntimeException",
                "errorMessage" to "OPS"
            )
        )
        o.verifyNoMoreInteractions()
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogPrintsOutWhenExceptionInDebug() {
        val aopProxy = compile(
            """
            open class Target {
                @Log.out
                open fun test(): String {
                    throw RuntimeException("OPS")
                }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        val o = Mockito.inOrder(log)
        reset(log, Level.DEBUG)

        assertThrows<RuntimeException> { aopProxy.invoke<Any>("test") }

        o.verify(log).warn(outData.capture(), ArgumentMatchers.eq("<"), ArgumentMatchers.any(Throwable::class.java))
        verifyOutData(
            mapOf(
                "errorType" to "java.lang.RuntimeException",
                "errorMessage" to "OPS"
            )
        )
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogInPrintsIn() {
        val aopProxy = compile(
            """
            open class Target {
                @Log.`in`
                open fun test() {}
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        aopProxy.invoke<Any>("test")

        val o = Mockito.inOrder(log)
        o.verify(log).info(">")
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogOutPrintsOut() {
        val aopProxy = compile(
            """
            open class Target {
                @Log.out
                open fun test() {}
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        aopProxy.invoke<Any>("test")

        val o = Mockito.inOrder(log)
        o.verify(log).info("<")
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogArgs() {
        val aopProxy = compile(
            """
            open class Target {
                @Log.`in`
                open fun test(arg1: String, @Log(TRACE) arg2: String, @Log.off arg3: String) {}
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        aopProxy.invoke<Any>("test", "test1", "test2", "test3")

        var o = Mockito.inOrder(log)
        o.verify(log).isDebugEnabled
        o.verify(log).info(">")
        o.verifyNoMoreInteractions()

        reset(log, Level.DEBUG)
        aopProxy.invoke<Any>("test", "test1", "test2", "test3")
        o = Mockito.inOrder(log)
        o.verify(log).isDebugEnabled
        o.verify(log).info(inData.capture(), ArgumentMatchers.eq(">"))
        o.verifyNoMoreInteractions()
        verifyInData(mapOf("arg1" to "test1"))
        o.verify(log).isTraceEnabled
        o.verifyNoMoreInteractions()

        reset(log, Level.TRACE)
        aopProxy.invoke<Any>("test", "test1", "test2", "test3")
        o = Mockito.inOrder(log)
        o.verify(log).isDebugEnabled
        o.verify(log).info(inData.capture(), ArgumentMatchers.eq(">"))
        o.verifyNoMoreInteractions()
        verifyInData(mapOf("arg1" to "test1", "arg2" to "test2"))
        o.verify(log).isTraceEnabled
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogArgsSameLevelAsIn() {
        val aopProxy = compile(
            """
            open class Target {
              @Log.`in`
              open fun test(@Log(INFO) arg1: String, @Log(TRACE) arg2: String, @Log.off arg3: String) {}
            }
            """.trimIndent()
        )
        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!
        reset(log, Level.INFO)
        aopProxy.invoke<Any>("test", "test1", "test2", "test3")
        val o = Mockito.inOrder(log)
        o.verify(log).isInfoEnabled
        o.verify(log).info(inData.capture(), ArgumentMatchers.eq(">"))
        o.verifyNoMoreInteractions()
        verifyInData(mapOf("arg1" to "test1"))
        o.verify(log).isTraceEnabled
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogResults() {
        val aopProxy = compile(
            """
            open class Target {
              @Log.out
              open fun test(): String { return "test-result" }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        reset(log, Level.INFO)
        aopProxy.invoke<Any>("test")
        var o = Mockito.inOrder(log)
        o.verify(log).isDebugEnabled()
        o.verify(log).info("<")
        o.verifyNoMoreInteractions()

        reset(log, Level.DEBUG)
        aopProxy.invoke<Any>("test")
        o = Mockito.inOrder(log)
        o.verify(log).isDebugEnabled()
        o.verify(log).info(outData.capture(), ArgumentMatchers.eq("<"))
        o.verifyNoMoreInteractions()
        verifyOutData(mapOf("out" to "test-result"))
    }

    @Test
    fun testLogResultsOff() {
        val aopProxy = compile(
            """
            open class Target {
              @Log.out
              @Log.off
              open fun test(): String { return "test-result" }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        reset(log, Level.INFO)
        aopProxy.invoke<Any>("test")
        var o = Mockito.inOrder(log)
        o.verify(log).info("<")
        o.verifyNoMoreInteractions()

        reset(log, Level.DEBUG)
        aopProxy.invoke<Any>("test")
        o = Mockito.inOrder(log)
        o.verify(log).info("<")
        o.verifyNoMoreInteractions()
    }

    @Test
    fun logResultSameLevelAsOut() {
        val aopProxy = compile(
            """
            open class Target {
              @Log.out
              @Log.result(INFO)
              open fun test(): String { return "test-result" }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        reset(log, Level.INFO)
        aopProxy.invoke<Any>("test")
        val o = Mockito.inOrder(log)
        o.verify(log).info(outData.capture(), ArgumentMatchers.eq("<"))
        o.verifyNoMoreInteractions()
        verifyOutData(mapOf("out" to "test-result"))
    }

    @Test
    fun testLogArgWithMapper() {
        compile0(listOf(AopSymbolProcessorProvider()),
            """
            open class Target {
                @Log.`in`
                open fun test(arg1: String) {}
            }
        """.trimIndent(), """
            class MyLogArgMapper : ru.tinkoff.kora.logging.common.arg.StructuredArgumentMapper<String> {
              override fun write(gen: tools.jackson.core.JsonGenerator, value: String) {
                gen.writeString("mapped-" + value)
              }
            }
        """.trimIndent()
        )
        compileResult.assertSuccess()

        val aopProxy = TestObject(loadClass("\$Target__AopProxy").kotlin, new("\$Target__AopProxy", factory, new("MyLogArgMapper")))

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!
        reset(log, Level.DEBUG)
        aopProxy.invoke<Any>("test", "arg1")
        val o = Mockito.inOrder(log)
        o.verify(log).isDebugEnabled
        o.verify(log).info(inData.capture(), ArgumentMatchers.eq(">"))
        o.verifyNoMoreInteractions()
        verifyInData(mapOf("arg1" to "mapped-arg1"))
        o.verify(log).isDebugEnabled
        o.verifyNoMoreInteractions()
    }

}

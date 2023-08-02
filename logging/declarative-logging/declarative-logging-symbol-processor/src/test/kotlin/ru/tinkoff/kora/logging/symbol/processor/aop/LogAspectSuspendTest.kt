package ru.tinkoff.kora.logging.symbol.processor.aop

import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.slf4j.event.Level
import java.util.*

class LogAspectSuspendTest : AbstractLogAspectTest() {

    @Test
    fun testLogPrintsInAndOut() {
        val aopProxy = compile("""
            open class Target {
                @Log
                open suspend fun test() {}
            }
        """.trimIndent())

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        aopProxy.invoke<Any>("test")

        val o = Mockito.inOrder(log)
        o.verify(log).info(">")
        o.verify(log).info("<")
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogInPrintsIn() {
        val aopProxy = compile("""
            open class Target {
                @Log.`in`
                open suspend fun test() {}
            }
        """.trimIndent())

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        aopProxy.invoke<Any>("test")

        val o = Mockito.inOrder(log)
        o.verify(log).info(">")
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogOutPrintsOut() {
        val aopProxy = compile("""
            open class Target {
                @Log.out
                open suspend fun test() {}
            }
        """.trimIndent())

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        aopProxy.invoke<Any>("test")

        val o = Mockito.inOrder(log)
        o.verify(log).info("<")
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogArgs() {
        val aopProxy = compile("""
            open class Target {
                @Log.`in`
                open suspend fun test(arg1: String, @Log(TRACE) arg2: String, @Log.off arg3: String) {}
            }
        """.trimIndent())

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
              open suspend fun test(@Log(INFO) arg1: String, @Log(TRACE) arg2: String, @Log.off arg3: String) {}
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
        val aopProxy = compile("""
            open class Target {
              @Log.out
              open suspend fun test(): String { return "test-result" }
            }
        """.trimIndent())

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
        val aopProxy = compile("""
            open class Target {
              @Log.out
              @Log.off
              open suspend fun test(): String { return "test-result" }
            }
        """.trimIndent())

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
        val aopProxy = compile("""
            open class Target {
              @Log.out
              @Log.result(INFO)
              open suspend fun test(): String { return "test-result" }
            }
        """.trimIndent())

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        reset(log, Level.INFO)
        aopProxy.invoke<Any>("test")
        val o = Mockito.inOrder(log)
        o.verify(log).info(outData.capture(), ArgumentMatchers.eq("<"))
        o.verifyNoMoreInteractions()
        verifyOutData(mapOf("out" to "test-result"))
    }
}

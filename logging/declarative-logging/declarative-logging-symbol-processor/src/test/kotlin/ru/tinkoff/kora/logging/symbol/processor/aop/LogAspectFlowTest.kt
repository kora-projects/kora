package ru.tinkoff.kora.logging.symbol.processor.aop

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.slf4j.event.Level
import java.util.*

class LogAspectFlowTest : AbstractLogAspectTest() {

    @Test
    fun testLogPrintsInAndOut() {
        val aopProxy = compile(
            """
            open class Target {
                @Log
                open fun test(): Flow<Unit> = flow { }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        val o = Mockito.inOrder(log)
        reset(log, Level.INFO)

        runBlocking { (aopProxy.invoke<Any>("test") as Flow<Any>).toList() }

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
                open fun test(): Flow<Unit> = flow { 
                    throw RuntimeException("OPS") 
                }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        val o = Mockito.inOrder(log)
        reset(log, Level.WARN)

        assertThrows<RuntimeException> {
            runBlocking { (aopProxy.invoke<Any>("test") as Flow<Any>).toList() }
        }

        o.verify(log).warn(outData.capture(), ArgumentMatchers.eq("<"))
        verifyOutData(
            mapOf(
                "errorType" to "java.lang.RuntimeException",
                "errorMessage" to "OPS"
            )
        )
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogPrintsInAndOutWhenExceptionInDebug() {
        val aopProxy = compile(
            """
            open class Target {
                @Log
                open fun test(): Flow<Unit> = flow { 
                    throw RuntimeException("OPS") 
                }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        val o = Mockito.inOrder(log)
        reset(log, Level.DEBUG)

        assertThrows<RuntimeException> {
            runBlocking { (aopProxy.invoke<Any>("test") as Flow<Any>).toList() }
        }

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
                open fun test(): Flow<String> = flow { throw RuntimeException("OPS") }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        val o = Mockito.inOrder(log)
        reset(log, Level.WARN)

        assertThrows<RuntimeException> {
            runBlocking { (aopProxy.invoke<Any>("test") as Flow<Any>).toList() }
        }

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
                open fun test(): Flow<String> = flow { throw RuntimeException("OPS") }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        val o = Mockito.inOrder(log)
        reset(log, Level.DEBUG)

        assertThrows<RuntimeException> {
            runBlocking { (aopProxy.invoke<Any>("test") as Flow<Any>).toList() }
        }

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
                open fun test(): Flow<Unit> = flow { }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!


        val o = Mockito.inOrder(log)
        reset(log, Level.INFO)

        runBlocking { (aopProxy.invoke<Any>("test") as Flow<Any>).toList() }

        o.verify(log).info(">")
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogOutPrintsOut() {
        val aopProxy = compile(
            """
            open class Target {
                @Log.out
                open fun test(): Flow<Unit> = flow { }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        val o = Mockito.inOrder(log)
        reset(log, Level.INFO)

        runBlocking { (aopProxy.invoke<Any>("test") as Flow<Any>).toList() }

        o.verify(log).info("<")
        o.verifyNoMoreInteractions()
    }

    @Test
    fun testLogArgs() {
        val aopProxy = compile(
            """
            open class Target {
                @Log.`in`
                open fun test(arg1: String, @Log(TRACE) arg2: String, @Log.off arg3: String): Flow<Unit> = flow { }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!


        reset(log, Level.INFO)

        runBlocking { (aopProxy.invoke<Any>("test", "test1", "test2", "test3") as Flow<Any>).toList() }

        var o = Mockito.inOrder(log)
        o.verify(log).isDebugEnabled
        o.verify(log).info(">")
        o.verifyNoMoreInteractions()

        reset(log, Level.DEBUG)

        runBlocking { (aopProxy.invoke<Any>("test", "test1", "test2", "test3") as Flow<Any>).toList() }

        o = Mockito.inOrder(log)
        o.verify(log).isDebugEnabled
        o.verify(log).info(inData.capture(), ArgumentMatchers.eq(">"))
        o.verifyNoMoreInteractions()
        verifyInData(mapOf("arg1" to "test1"))
        o.verify(log).isTraceEnabled
        o.verifyNoMoreInteractions()

        reset(log, Level.TRACE)

        runBlocking { (aopProxy.invoke<Any>("test", "test1", "test2", "test3") as Flow<Any>).toList() }

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
              open fun test(@Log(INFO) arg1: String, @Log(TRACE) arg2: String, @Log.off arg3: String): Flow<Unit> = flow { }
            }
            """.trimIndent()
        )
        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        reset(log, Level.INFO)

        runBlocking { (aopProxy.invoke<Any>("test", "test1", "test2", "test3") as Flow<Any>).toList() }

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
              open fun test(): Flow<String> = flow { emit("test-result") }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        reset(log, Level.INFO)

        runBlocking { (aopProxy.invoke<Any>("test") as Flow<String>).toList() }

        var o = Mockito.inOrder(log)
        o.verify(log).isDebugEnabled()
        o.verify(log).info("<<<")
        o.verify(log).info("<")
        o.verifyNoMoreInteractions()

        reset(log, Level.DEBUG)

        runBlocking { (aopProxy.invoke<Any>("test") as Flow<String>).toList() }

        o = Mockito.inOrder(log)
        o.verify(log).isDebugEnabled()
        o.verify(log).info(outData.capture(), ArgumentMatchers.eq("<<<"))
        o.verify(log).info("<")
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
              open fun test(): Flow<String> = flow { emit("test-result") }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        reset(log, Level.INFO)

        runBlocking { (aopProxy.invoke<Any>("test") as Flow<String>).toList() }

        var o = Mockito.inOrder(log)
        o.verify(log).info("<<<")
        o.verify(log).info("<")
        o.verifyNoMoreInteractions()

        reset(log, Level.DEBUG)

        runBlocking { (aopProxy.invoke<Any>("test") as Flow<String>).toList() }

        o = Mockito.inOrder(log)
        o.verify(log).info("<<<")
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
              open fun test(): Flow<String> = flow { emit("test-result") }
            }
        """.trimIndent()
        )

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        reset(log, Level.INFO)

        runBlocking { (aopProxy.invoke<Any>("test") as Flow<String>).toList() }

        val o = Mockito.inOrder(log)
        o.verify(log).info(outData.capture(), ArgumentMatchers.eq("<<<"))
        o.verify(log).info("<")
        o.verifyNoMoreInteractions()
        verifyOutData(mapOf("out" to "test-result"))
    }
}

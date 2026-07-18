package io.koraframework.logging.symbol.processor.aop

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.slf4j.event.Level
import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.json.common.JsonWriter
import io.koraframework.json.common.writer.ListJsonWriter
import io.koraframework.json.common.writer.MapJsonWriter
import io.koraframework.json.ksp.JsonSymbolProcessorProvider
import io.koraframework.logging.common.arg.JsonStructuredArgumentMapper
import io.koraframework.logging.common.arg.MaskedStructuredArgumentMapper
import io.koraframework.logging.common.masking.MaskingMetadata
import io.koraframework.logging.symbol.processor.MaskingMetadataSymbolProcessorProvider
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
            class MyLogArgMapper : io.koraframework.logging.common.arg.StructuredArgumentMapper<String> {
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

    @Test
    fun testMaskingMetadataSupportsNestedGenericContainers() {
        compile0(
            listOf(JsonSymbolProcessorProvider(), MaskingMetadataSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
            @Mask
            @Json
            data class Credentials(@Mask val secret: String)
            """.trimIndent(),
            """
            @Mask
            @Json
            data class User(val nestedList: List<List<Credentials>>, val nestedMap: Map<String, List<Credentials>>)
            """.trimIndent(),
            """
            open class Target {
                @Log.`in`
                open fun test(@Mask arg1: User) {}
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val credentialsWriter = new("\$Credentials_JsonWriter") as JsonWriter<Any?>
        val listWriter = ListJsonWriter(credentialsWriter)
        val nestedListWriter = ListJsonWriter(listWriter)
        val nestedMapWriter = MapJsonWriter(listWriter)
        val userWriter = new("\$User_JsonWriter", nestedListWriter, nestedMapWriter) as JsonWriter<Any?>
        val metadata = new("\$User_MaskingMetadata") as MaskingMetadata<Any?>
        val mapper = MaskedStructuredArgumentMapper(userWriter, metadata)
        val aopProxy = TestObject(loadClass("\$Target__AopProxy").kotlin, new("\$Target__AopProxy", factory, mapper))

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        val credentials1 = new("Credentials", "secret-1")
        val credentials2 = new("Credentials", "secret-2")
        val user = new("User", listOf(listOf(credentials1)), mapOf("key" to listOf(credentials2)))
        reset(log, Level.DEBUG)
        aopProxy.invoke<Any>("test", user)
        val o = Mockito.inOrder(log)
        o.verify(log).isDebugEnabled
        o.verify(log).info(inData.capture(), ArgumentMatchers.eq(">"))
        o.verifyNoMoreInteractions()
        verifyInJson("{\"arg1\":{\"nestedList\":[[{\"secret\":\"***\"}]],\"nestedMap\":{\"key\":[{\"secret\":\"***\"}]}}}")
    }

    @Test
    fun testLogResultWithJsonMapperTag() {
        compile0(
            listOf(JsonSymbolProcessorProvider(), MaskingMetadataSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
            @Json
            data class TestRecord(val value: String)
            """.trimIndent(),
            """
            open class Target {
                @Log.out
                @Json
                open fun test(): TestRecord {
                    return TestRecord("test-value")
                }
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val writer = new("\$TestRecord_JsonWriter") as JsonWriter<Any?>
        val mapper = JsonStructuredArgumentMapper(writer)
        val aopProxy = TestObject(loadClass("\$Target__AopProxy").kotlin, new("\$Target__AopProxy", factory, mapper))

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        reset(log, Level.DEBUG)
        aopProxy.invoke<Any>("test")
        val o = Mockito.inOrder(log)
        o.verify(log).isDebugEnabled
        o.verify(log).info(outData.capture(), ArgumentMatchers.eq("<"))
        o.verifyNoMoreInteractions()
        verifyOutJson("{\"out\":{\"value\":\"test-value\"}}")
    }

    @Test
    fun testLogResultWithMaskedMapperTag() {
        compile0(
            listOf(JsonSymbolProcessorProvider(), MaskingMetadataSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
            @Mask
            @Json
            data class User(val name: String, @Mask(mode = Mask.Mode.KEEP_LAST, keep = 2) val token: String)
            """.trimIndent(),
            """
            open class Target {
                @Log.out
                @Mask
                open fun test(): User {
                    return User("user", "secret")
                }
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val writer = new("\$User_JsonWriter") as JsonWriter<Any?>
        val metadata = new("\$User_MaskingMetadata") as MaskingMetadata<Any?>
        val mapper = MaskedStructuredArgumentMapper(writer, metadata)
        val aopProxy = TestObject(loadClass("\$Target__AopProxy").kotlin, new("\$Target__AopProxy", factory, mapper))

        Mockito.verify(factory).getLogger(testPackage() + ".Target.test")
        val log = Objects.requireNonNull(loggers[testPackage() + ".Target.test"])!!

        reset(log, Level.DEBUG)
        aopProxy.invoke<Any>("test")
        val o = Mockito.inOrder(log)
        o.verify(log).isDebugEnabled
        o.verify(log).info(outData.capture(), ArgumentMatchers.eq("<"))
        o.verifyNoMoreInteractions()
        verifyOutJson("{\"out\":{\"name\":\"user\",\"token\":\"***et\"}}")
    }

}

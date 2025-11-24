package ru.tinkoff.kora.logging.symbol.processor.aop.mdc

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.ksp.common.TestUtils
import ru.tinkoff.kora.logging.common.MDC
import java.util.*

class MdcKoraAspectTest : AbstractMdcAspectTest() {

    private val contextHolder = MDCContextHolder()

    private inline fun <T> withMdc(crossinline callback: () -> T): T {
        return ScopedValue.where(MDC.VALUE, MDC()).call<T, RuntimeException> { callback() }
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    fun testMdc(source: String) {
        val aopProxy = compile0(listOf(AopSymbolProcessorProvider()), source.trimIndent())

        withMdc {
            invokeMethod(aopProxy)

            val context = contextHolder.get()
                ?.mapValues { it.value.writeToString() }

            assertEquals(mapOf("key" to "\"value\"", "key1" to "\"value2\"", "123" to "\"test\""), context)
            assertEquals(emptyMap<String, String>(), currentContext())
        }
    }

    @Test
    fun testMdcWithCode() {
        val aopProxy = compile0(
            listOf(AopSymbolProcessorProvider()),
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {
                @Mdc(key = "key", value = "${UUID.randomUUID()}")
                open fun test(s: String): Int? {
                    mdcContextHolder.set(MDC.get().values())
                    return null
                }
            }
        """.trimIndent()
        )

        withMdc {
            invokeMethod(aopProxy)

            val context = contextHolder.get()
                ?.mapValues { it.value.writeToString() }

            val value = context?.get("key")
            assertNotNull(value)
            assertDoesNotThrow { UUID.fromString(value.substring(1, value.length - 1)) }
            assertEquals(emptyMap<String, String>(), currentContext())
        }
    }

    @Test
    fun testGlobalMdc() {
        val aopProxy = compile0(
            listOf(AopSymbolProcessorProvider()),
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {
                @Mdc(key = "key", value = "value", global = true)
                @Mdc(key = "key1", value = "value2")
                open fun test(@Mdc(key = "123", global = true) s: String): Int? {
                    mdcContextHolder.set(MDC.get().values())
                    return null
                }
            }
        """.trimIndent()
        )

        withMdc {
            invokeMethod(aopProxy)

            val context = contextHolder.get()
                ?.mapValues { it.value.writeToString() }

            assertEquals(mapOf("key" to "\"value\"", "key1" to "\"value2\"", "123" to "\"test\""), context)
            assertEquals(mapOf("key" to "\"value\"", "123" to "\"test\""), currentContext())
        }
    }

    @ParameterizedTest
    @MethodSource("provideGlobalSuspendTestCases")
    fun testGlobalMdcWithCoroutines(source: String) {
        val aopProxy = compile0(listOf(AopSymbolProcessorProvider()), source.trimIndent())
        aopProxy.assertFailure()
    }

    @Test
    fun testMdcWithNotEmptyContext() {
        val aopProxy = compile0(
            listOf(AopSymbolProcessorProvider()),
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {
                @Mdc(key = "key", value = "value")
                @Mdc(key = "key1", value = "value2")
                open fun test(@Mdc(key = "123") s: String): Int? {
                    mdcContextHolder.set(MDC.get().values())
                    return null
                }
            }
        """.trimIndent()
        )

        withMdc {
            MDC.put("key", "special-value")
            MDC.put("123", "special-123")
            invokeMethod(aopProxy)

            val context = contextHolder.get()
                ?.mapValues { it.value.writeToString() }

            assertEquals(mapOf("key" to "\"value\"", "key1" to "\"value2\"", "123" to "\"test\""), context)
            assertEquals(mapOf("key" to "\"special-value\"", "123" to "\"special-123\""), currentContext())
        }
    }

    private fun invokeMethod(aopProxy: TestUtils.ProcessingResult) {
        aopProxy.assertSuccess()

        val generatedClass = loadClass("\$TestMdc__AopProxy")
        val constructor = generatedClass.constructors.first()

        val testObject = TestObject(generatedClass.kotlin, constructor.newInstance(contextHolder))

        testObject.invoke<Int?>("test", "test")
    }

    private fun currentContext() = MDC.get().values().mapValues { it.value.writeToString() }

    companion object {

        @JvmStatic
        @Language("kotlin")
        private fun provideTestCases() = listOf(
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {
                @Mdc(key = "key", value = "value")
                @Mdc(key = "key1", value = "value2")
                open fun test(@Mdc(key = "123") s: String): Int? {
                    mdcContextHolder.set(MDC.get().values())
                    return null
                }
            }
        """,
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {
                @Mdc(key = "key", value = "value")
                @Mdc(key = "key1", value = "value2")
                open fun test(@Mdc(key = "123") s: String) {
                    mdcContextHolder.set(MDC.get().values())
                }
            }
        """
        )


        @JvmStatic
        @Language("kotlin")
        private fun provideGlobalSuspendTestCases() = listOf(
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {
                @Mdc(key = "key", value = "value", global = true)
                @Mdc(key = "key1", value = "value2")
                open suspend fun test(@Mdc(key = "123") s: String): Int? {
                    mdcContextHolder.set(MDC.get().values())
                    return null
                }
            }
        """,
            """
            open class TestMdc(
                private val mdcContextHolder: MDCContextHolder
            ) {
                @Mdc(key = "key", value = "value")
                @Mdc(key = "key1", value = "value2")
                open suspend fun test(@Mdc(key = "123", global = true) s: String) {
                    mdcContextHolder.set(MDC.get().values())
                }
            }
        """
        )
    }
}

package io.koraframework.validation.symbol.processor

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.application.graph.TypeRef
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.validation.common.ViolationException
import io.koraframework.validation.common.constraint.ValidatorModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ValidationGenericMethodTests : AbstractValidationSymbolProcessorTest(), ValidatorModule {

    private val processors = listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider())

    @Test
    fun functionTypeParameterArgumentAndResult() {
        compile0(
            processors,
            """
            @Component
            open class TestComponent {
                @Validate
                @NotBlank
                open fun <T : CharSequence> test(@NotBlank value: T): T = value
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val component = newObject("\$TestComponent__AopProxy", notBlankCharSequenceValidatorFactory())
        assertEquals("1", component.invoke<String>("test", "1"))
        assertThrows(ViolationException::class.java) { component.invoke<String>("test", " ") }
    }

    @Test
    fun functionTypeParameterNestedInArgumentAndResult() {
        compile0(
            processors,
            """
            @Component
            open class TestComponent {
                @Validate
                @Size(min = 1, max = 2)
                open fun <T : CharSequence> test(@Size(min = 1, max = 10) value: List<T>): List<T> = value
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val component = newObject("\$TestComponent__AopProxy", sizeListValidatorFactory(TypeRef.of(CharSequence::class.java)))
        assertEquals(listOf("1"), component.invoke<List<String>>("test", listOf("1")))
        assertThrows(ViolationException::class.java) { component.invoke<List<String>>("test", listOf<String>()) }
        assertThrows(ViolationException::class.java) { component.invoke<List<String>>("test", listOf("1", "2", "3")) }
    }

    @Test
    fun functionTypeParameterNestedInInvariantArgument() {
        compile0(
            processors,
            """
            @Component
            open class TestComponent {
                @Validate
                open fun <T : CharSequence> test(@Size(min = 1, max = 10) value: MutableList<T>): Int = value.size
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val component = newObject("\$TestComponent__AopProxy", sizeListValidatorFactory(TypeRef.of(CharSequence::class.java)))
        assertEquals(1, component.invoke<Int>("test", mutableListOf("1")))
        assertThrows(ViolationException::class.java) { component.invoke<Int>("test", mutableListOf<String>()) }
    }

    @Test
    fun genericMethodTypeParameterInListCompilesWithAllWarningsAsErrors() {
        allWarningsAsErrors = true
        compile0(
            processors,
            """
            @Component
            open class TestComponent {
                @Validate
                @Size(min = 1, max = 2)
                open fun <T : CharSequence> test(@Size(min = 1, max = 10) value: MutableList<T>): MutableList<T> = value
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val component = newObject("\$TestComponent__AopProxy", sizeListValidatorFactory(TypeRef.of(CharSequence::class.java)))
        assertEquals(mutableListOf("1"), component.invoke<MutableList<String>>("test", mutableListOf("1")))
        assertThrows(ViolationException::class.java) { component.invoke<MutableList<String>>("test", mutableListOf("1", "2", "3")) }
    }
}

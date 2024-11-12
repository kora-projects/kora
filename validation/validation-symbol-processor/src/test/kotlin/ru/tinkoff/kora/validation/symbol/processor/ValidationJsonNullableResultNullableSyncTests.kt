package ru.tinkoff.kora.validation.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.validation.common.ViolationException
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule

class ValidationJsonNullableResultNullableSyncTests : AbstractValidationSymbolProcessorTest(), ValidatorModule {

    @Test
    fun resultJsonNullableIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component                 
                open class TestComponent {
                    @Validate
                    open fun test(): JsonNullable<String>? {
                        return JsonNullable.undefined()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = compileResult.loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(): JsonNullable<String>? {
                        return JsonNullable.nullValue()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = compileResult.loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(): JsonNullable<String>? {
                        return JsonNullable.of("1")
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = compileResult.loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableNonNullIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    @Nonnull
                    open fun test(): JsonNullable<String>? {
                        return JsonNullable.undefined()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = compileResult.loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableNonNullIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    @Nonnull
                    open fun test(): JsonNullable<String>? {
                        return JsonNullable.nullValue()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = compileResult.loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableNonNullIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    @Nonnull
                    open fun test(): JsonNullable<String>? {
                        return JsonNullable.of("1")
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = compileResult.loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableWithValidatorIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    @NotBlank
                    @NotEmpty
                    open fun test(): JsonNullable<String>? {
                        return JsonNullable.undefined()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = compileResult.loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableWithValidatorIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    @NotBlank
                    @NotEmpty
                    open fun test(): JsonNullable<String>? {
                        return JsonNullable.nullValue()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = compileResult.loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        val ex = assertThrows(ViolationException::class.java) { component.invoke<Any>("test") }
        assertEquals(2, ex.violations.size)
    }

    @Test
    fun resultJsonNullableWithValidatorIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    @NotBlank
                    @NotEmpty
                    open fun test(): JsonNullable<String>? {
                        return JsonNullable.of("1")
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = compileResult.loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableWithValidatorFailFastIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate(failFast = true)
                    @NotBlank
                    @NotEmpty
                    open fun test(): JsonNullable<String>? {
                        return JsonNullable.undefined()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = compileResult.loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableWithValidatorFailFastIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate(failFast = true)
                    @NotBlank
                    @NotEmpty
                    open fun test(): JsonNullable<String>? {
                        return JsonNullable.nullValue()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = compileResult.loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        val ex = assertThrows(ViolationException::class.java) { component.invoke<Any>("test") }
        assertEquals(1, ex.violations.size)
    }

    @Test
    fun resultJsonNullableWithValidatorFailFastIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate(failFast = true)
                    @NotBlank
                    @NotEmpty
                    open fun test(): JsonNullable<String>? {
                        return JsonNullable.of("1")
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = compileResult.loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }
}

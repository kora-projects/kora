package ru.tinkoff.kora.validation.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.validation.common.ViolationException
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule

class ValidationJsonNullableResultRequiredSyncTests : AbstractValidationSymbolProcessorTest(), ValidatorModule {

    @Test
    fun resultJsonNullableIsUndefined() {
        compile(
            """
                @Component                 
                open class TestComponent {
                    @Validate
                    open fun test(): JsonNullable<String> {
                        return JsonNullable.undefined()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableIsNull() {
        compile(
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(): JsonNullable<String> {
                        return JsonNullable.nullValue()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableIsPresent() {
        compile(
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(): JsonNullable<String> {
                        return JsonNullable.of("1")
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableNonNullIsUndefined() {
        compile(
            """
                @Component
                open class TestComponent {
                    @Validate
                    @Nonnull
                    open fun test(): JsonNullable<String> {
                        return JsonNullable.undefined()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableNonNullIsNull() {
        compile(
            """
                @Component
                open class TestComponent {
                    @Validate
                    @Nonnull
                    open fun test(): JsonNullable<String> {
                        return JsonNullable.nullValue()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableNonNullIsPresent() {
        compile(
            """
                @Component
                open class TestComponent {
                    @Validate
                    @Nonnull
                    open fun test(): JsonNullable<String> {
                        return JsonNullable.of("1")
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableWithValidatorIsUndefined() {
        compile(
            """
                @Component
                open class TestComponent {
                    @Validate
                    @NotBlank
                    @NotEmpty
                    open fun test(): JsonNullable<String> {
                        return JsonNullable.undefined()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableWithValidatorIsNull() {
        compile(
            """
                @Component
                open class TestComponent {
                    @Validate
                    @NotBlank
                    @NotEmpty
                    open fun test(): JsonNullable<String> {
                        return JsonNullable.nullValue()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        val ex = assertThrows(ViolationException::class.java) { component.invoke<Any>("test") }
        assertEquals(2, ex.violations.size)
    }

    @Test
    fun resultJsonNullableWithValidatorIsPresent() {
        compile(
            """
                @Component
                open class TestComponent {
                    @Validate
                    @NotBlank
                    @NotEmpty
                    open fun test(): JsonNullable<String> {
                        return JsonNullable.of("1")
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableWithValidatorFailFastIsUndefined() {
        compile(
            """
                @Component
                open class TestComponent {
                    @Validate(failFast = true)
                    @NotBlank
                    @NotEmpty
                    open fun test(): JsonNullable<String> {
                        return JsonNullable.undefined()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }

    @Test
    fun resultJsonNullableWithValidatorFailFastIsNull() {
        compile(
            """
                @Component
                open class TestComponent {
                    @Validate(failFast = true)
                    @NotBlank
                    @NotEmpty
                    open fun test(): JsonNullable<String> {
                        return JsonNullable.nullValue()
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        val ex = assertThrows(ViolationException::class.java) { component.invoke<Any>("test") }
        assertEquals(1, ex.violations.size)
    }

    @Test
    fun resultJsonNullableWithValidatorFailFastIsPresent() {
        compile(
            """
                @Component
                open class TestComponent {
                    @Validate(failFast = true)
                    @NotBlank
                    @NotEmpty
                    open fun test(): JsonNullable<String> {
                        return JsonNullable.of("1")
                    }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow<Any> { component.invoke<Any>("test") }
    }
}

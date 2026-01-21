package ru.tinkoff.kora.validation.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.json.common.JsonValue
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.validation.common.ViolationException
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule

class ValidationJsonNullableArgumentValueSyncTests : AbstractValidationSymbolProcessorTest(), ValidatorModule {

    @Test
    fun argumentJsonNullableIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(arg: JsonNullable<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertDoesNotThrow { component.invoke<Any>("test", JsonValue.undefined<Any>()) }
    }

    @Test
    fun argumentJsonNullableIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(arg: JsonNullable<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertDoesNotThrow { component.invoke<Any>("test", JsonValue.nullValue<Any>()) }
    }

    @Test
    fun argumentJsonNullableIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(arg: JsonNullable<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertDoesNotThrow { component.invoke<Any>("test", JsonValue.of<String>("1")) }
    }

    @Test
    fun argumentJsonNullableNonNullIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(arg: @NonNull JsonNullable<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertThrows(
            ViolationException::class.java
        ) { component.invoke<Any>("test", JsonValue.undefined<Any>()) }
    }

    @Test
    fun argumentJsonNullableNonNullIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(arg: @NonNull JsonNullable<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertThrows(
            ViolationException::class.java
        ) { component.invoke<Any>("test", JsonValue.nullValue<Any>()) }
    }

    @Test
    fun argumentJsonNullableNonNullIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(arg: @NonNull JsonNullable<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy")
        assertDoesNotThrow { component.invoke<Any>("test", JsonValue.of<String>("1")) }
    }

    @Test
    fun argumentJsonNullableWithValidatorIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(@NotBlank @NotEmpty arg: JsonNullable<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow { component.invoke<Any>("test", JsonValue.undefined<Any>()) }
    }

    @Test
    fun argumentJsonNullableWithValidatorIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(@NotBlank @NotEmpty arg: JsonNullable<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        val ex = assertThrows(
            ViolationException::class.java
        ) { component.invoke<Any>("test", JsonValue.nullValue<Any>()) }
        assertEquals(2, ex.violations.size)
    }

    @Test
    fun argumentJsonNullableWithValidatorIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(@NotBlank @NotEmpty arg: JsonNullable<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow { component.invoke<Any>("test", JsonValue.of<String>("1")) }
    }

    @Test
    fun argumentJsonNullableWithValidatorFailFastIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate(failFast = true)
                    open fun test(@NotBlank @NotEmpty arg: JsonNullable<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow { component.invoke<Any>("test", JsonValue.undefined<Any>()) }
    }

    @Test
    fun argumentJsonNullableWithValidatorFailFastIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate(failFast = true)
                    open fun test(@NotBlank @NotEmpty arg: JsonNullable<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        val ex = assertThrows(
            ViolationException::class.java
        ) { component.invoke<Any>("test", JsonValue.nullValue<Any>()) }
        assertEquals(1, ex.violations.size)
    }

    @Test
    fun argumentJsonNullableWithValidatorFailFastIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate(failFast = true)
                    open fun test(@NotBlank @NotEmpty arg: JsonNullable<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow { component.invoke<Any>("test", JsonValue.of<String>("1")) }
    }

}

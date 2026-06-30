package io.koraframework.validation.symbol.processor

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.json.common.JsonValue
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.validation.common.ViolationException
import io.koraframework.validation.common.constraint.ValidatorModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ValidationJsonNullableArgumentValueSyncTests : AbstractValidationSymbolProcessorTest(), ValidatorModule {

    @Test
    fun argumentJsonNullableIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(arg: JsonValue<String>?) { }
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
                    open fun test(arg: JsonValue<String>?) { }
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
                    open fun test(arg: JsonValue<String>?) { }
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
                    open fun test(arg: @NonNull JsonValue<String>?) { }
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
                    open fun test(arg: @NonNull JsonValue<String>?) { }
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
                    open fun test(@NotBlank @NotEmpty arg: JsonValue<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory())
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
                    open fun test(@NotBlank @NotEmpty arg: JsonValue<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory())
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
                    open fun test(@NotBlank @NotEmpty arg: JsonValue<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory())
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
                    open fun test(@NotBlank @NotEmpty arg: JsonValue<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory())
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
                    open fun test(@NotBlank @NotEmpty arg: JsonValue<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory())
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
                    open fun test(@NotBlank @NotEmpty arg: JsonValue<String>?) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory())
        assertDoesNotThrow { component.invoke<Any>("test", JsonValue.of<String>("1")) }
    }
}

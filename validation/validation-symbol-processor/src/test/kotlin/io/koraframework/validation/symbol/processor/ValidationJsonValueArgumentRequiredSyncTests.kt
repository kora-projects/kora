package io.koraframework.validation.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
<<<<<<<< HEAD:validation/validation-symbol-processor/src/test/kotlin/io/koraframework/validation/symbol/processor/ValidationJsonNullableArgumentRequiredSyncTests.kt
import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.json.common.JsonNullable
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.validation.common.ViolationException
import io.koraframework.validation.common.constraint.ValidatorModule
========
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.json.common.JsonValue
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.validation.common.ViolationException
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-symbol-processor/src/test/kotlin/io/koraframework/validation/symbol/processor/ValidationJsonValueArgumentRequiredSyncTests.kt

class ValidationJsonValueArgumentRequiredSyncTests : AbstractValidationSymbolProcessorTest(), ValidatorModule {

    @Test
    fun argumentJsonNullableIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(arg: JsonNullable<String>) { }
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
                    open fun test(arg: JsonNullable<String>) { }
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
                    open fun test(arg: JsonNullable<String>) { }
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
                    open fun test(arg: @NonNull JsonNullable<String>) { }
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
                    open fun test(arg: @NonNull JsonNullable<String>) { }
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
                    open fun test(arg: @NonNull JsonNullable<String>) { }
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
                    open fun test(@NotBlank @NotEmpty arg: JsonNullable<String>) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

<<<<<<<< HEAD:validation/validation-symbol-processor/src/test/kotlin/io/koraframework/validation/symbol/processor/ValidationJsonNullableArgumentRequiredSyncTests.kt
        val component = newObject("\$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory())
        assertDoesNotThrow { component.invoke<Any>("test", JsonNullable.undefined<Any>()) }
========
        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow { component.invoke<Any>("test", JsonValue.undefined<Any>()) }
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-symbol-processor/src/test/kotlin/io/koraframework/validation/symbol/processor/ValidationJsonValueArgumentRequiredSyncTests.kt
    }

    @Test
    fun argumentJsonNullableWithValidatorIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate
                    open fun test(@NotBlank @NotEmpty arg: JsonNullable<String>) { }
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
                    open fun test(@NotBlank @NotEmpty arg: JsonNullable<String>) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

<<<<<<<< HEAD:validation/validation-symbol-processor/src/test/kotlin/io/koraframework/validation/symbol/processor/ValidationJsonNullableArgumentRequiredSyncTests.kt
        val component = newObject("\$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory())
        assertDoesNotThrow { component.invoke<Any>("test", JsonNullable.of<String>("1")) }
========
        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow { component.invoke<Any>("test", JsonValue.of<String>("1")) }
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-symbol-processor/src/test/kotlin/io/koraframework/validation/symbol/processor/ValidationJsonValueArgumentRequiredSyncTests.kt
    }

    @Test
    fun argumentJsonNullableWithValidatorFailFastIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate(failFast = true)
                    open fun test(@NotBlank @NotEmpty arg: JsonNullable<String>) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

<<<<<<<< HEAD:validation/validation-symbol-processor/src/test/kotlin/io/koraframework/validation/symbol/processor/ValidationJsonNullableArgumentRequiredSyncTests.kt
        val component = newObject("\$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory())
        assertDoesNotThrow { component.invoke<Any>("test", JsonNullable.undefined<Any>()) }
========
        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow { component.invoke<Any>("test", JsonValue.undefined<Any>()) }
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-symbol-processor/src/test/kotlin/io/koraframework/validation/symbol/processor/ValidationJsonValueArgumentRequiredSyncTests.kt
    }

    @Test
    fun argumentJsonNullableWithValidatorFailFastIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                @Component
                open class TestComponent {
                    @Validate(failFast = true)
                    open fun test(@NotBlank @NotEmpty arg: JsonNullable<String>) { }
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
                    open fun test(@NotBlank @NotEmpty arg: JsonNullable<String>) { }
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestComponent__AopProxy")
        assertThat(validatorClass).isNotNull()

<<<<<<<< HEAD:validation/validation-symbol-processor/src/test/kotlin/io/koraframework/validation/symbol/processor/ValidationJsonNullableArgumentRequiredSyncTests.kt
        val component = newObject("\$TestComponent__AopProxy", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory())
        assertDoesNotThrow { component.invoke<Any>("test", JsonNullable.of<String>("1")) }
========
        val component = newObject("\$TestComponent__AopProxy", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory())
        assertDoesNotThrow { component.invoke<Any>("test", JsonValue.of<String>("1")) }
>>>>>>>> 82ba3753b (JsonNullable refactored to JsonValue & JsonNullable & JsonUndefined contracts for Java):validation/validation-symbol-processor/src/test/kotlin/io/koraframework/validation/symbol/processor/ValidationJsonValueArgumentRequiredSyncTests.kt
    }

}

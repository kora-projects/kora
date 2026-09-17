package io.koraframework.validation.symbol.processor

import io.koraframework.json.common.JsonNullable
import io.koraframework.json.common.JsonValue
import io.koraframework.json.common.JsonUndefined
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.validation.common.ValidationContext
import io.koraframework.validation.common.Validator
import io.koraframework.validation.common.constraint.ValidatorModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ValidationJsonValueFieldRequiredTests : AbstractValidationSymbolProcessorTest(), ValidatorModule {

    @Test
    fun fieldJsonNullableIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(val field: JsonValue<String>)
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()

        val validator = newObject("\$TestRecord_Validator").objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonValue.undefined<String>()).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(val field: JsonValue<String>)

                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()

        val validator = newObject("\$TestRecord_Validator").objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonValue.nullValue<String>()).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(val field: JsonValue<String>)

                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()

        val validator = newObject("\$TestRecord_Validator").objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonValue.of("")).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableNonNullIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotNull val field: JsonValue<String>)

                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()

        val validator = newObject("\$TestRecord_Validator").objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonValue.undefined<String>()).objectInstance)
        assertEquals(1, violations.size)
    }

    @Test
    fun fieldJsonNullableNonNullIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotNull val field: JsonValue<String>)

                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()

        val validator = newObject("\$TestRecord_Validator").objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonValue.nullValue<String>()).objectInstance)
        assertEquals(1, violations.size)
    }

    @Test
    fun fieldJsonNullableNonNullIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotNull val field: JsonValue<String>)

                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()

        val validator = newObject("\$TestRecord_Validator").objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonValue.of("")).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableWithValidatorIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonValue<String>)

                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonValue.undefined<String>()).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableWithValidatorIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord<T>(@field:NotBlank @field:NotEmpty val field: JsonValue<T>)

                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonValue.nullValue<String>()).objectInstance)
        assertEquals(2, violations.size)
    }

    @Test
    fun fieldJsonNullableWithValidatorIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonValue<String>)

                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonValue.of("1")).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableTypeWithValidatorIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonValue<String>)

                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue<String>()).objectInstance)
        assertEquals(2, violations.size)
    }

    @Test
    fun fieldJsonNullableTypeWithValidatorIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonValue<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.of("1")).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonUndefinedWithValidatorIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonUndefined<String>)

                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonUndefined.undefined<String>()).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonUndefinedWithValidatorIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonUndefined<String>)

                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonUndefined.of("1")).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableWithValidatorFailFastIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonValue<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonValue.undefined<String>()).objectInstance, ValidationContext.failFast())
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableWithValidatorFailFastIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonValue<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonValue.nullValue<String>()).objectInstance, ValidationContext.failFast())
        assertEquals(1, violations.size)
    }

    @Test
    fun fieldJsonNullableWithValidatorFailFastIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonValue<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringValidatorFactory(), notEmptyStringValidatorFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonValue.of("1")).objectInstance, ValidationContext.failFast())
        assertEquals(0, violations.size)
    }
}

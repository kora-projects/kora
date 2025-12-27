package ru.tinkoff.kora.validation.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.json.common.JsonNullable
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.validation.common.ValidationContext
import ru.tinkoff.kora.validation.common.Validator
import ru.tinkoff.kora.validation.common.constraint.ValidatorModule

class ValidationJsonNullableFieldRequiredTests : AbstractValidationSymbolProcessorTest(), ValidatorModule {

    @Test
    fun fieldJsonNullableIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(val field: JsonNullable<String>)
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()

        val validator = newObject("\$TestRecord_Validator").objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.undefined<String>()).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(val field: JsonNullable<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()

        val validator = newObject("\$TestRecord_Validator").objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue<String>()).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(val field: JsonNullable<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()

        val validator = newObject("\$TestRecord_Validator").objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.of("")).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableNonNullIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotNull val field: JsonNullable<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()

        val validator = newObject("\$TestRecord_Validator").objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.undefined<String>()).objectInstance)
        assertEquals(1, violations.size)
    }

    @Test
    fun fieldJsonNullableNonNullIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotNull val field: JsonNullable<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()

        val validator = newObject("\$TestRecord_Validator").objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue<String>()).objectInstance)
        assertEquals(1, violations.size)
    }

    @Test
    fun fieldJsonNullableNonNullIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotNull val field: JsonNullable<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()

        val validator = newObject("\$TestRecord_Validator").objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.of("")).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableWithValidatorIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonNullable<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.undefined<String>()).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableWithValidatorIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord<T>(@field:NotBlank @field:NotEmpty val field: JsonNullable<T>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue<String>()).objectInstance)
        assertEquals(2, violations.size)
    }

    @Test
    fun fieldJsonNullableWithValidatorIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonNullable<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.of("1")).objectInstance)
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableWithValidatorFailFastIsUndefined() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonNullable<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.undefined<String>()).objectInstance, ValidationContext.failFast())
        assertEquals(0, violations.size)
    }

    @Test
    fun fieldJsonNullableWithValidatorFailFastIsNull() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonNullable<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.nullValue<String>()).objectInstance, ValidationContext.failFast())
        assertEquals(1, violations.size)
    }

    @Test
    fun fieldJsonNullableWithValidatorFailFastIsPresent() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                    @Valid
                    data class TestRecord(@field:NotBlank @field:NotEmpty val field: JsonNullable<String>)
                    
                    """.trimIndent()
        )
        compileResult.assertSuccess()

        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        assertThat(validatorClass.constructors).hasSize(1)

        val validator = newObject("\$TestRecord_Validator", notBlankStringConstraintFactory(), notEmptyStringConstraintFactory()).objectInstance as Validator<Any>
        val violations = validator.validate(newObject("TestRecord", JsonNullable.of("1")).objectInstance, ValidationContext.failFast())
        assertEquals(0, violations.size)
    }

}

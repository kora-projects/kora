package io.koraframework.validation.symbol.processor

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.validation.common.Validator
import io.koraframework.validation.common.ViolationException
import io.koraframework.validation.common.constraint.ValidatorModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Annotation values and declaration names are copied into the generated sources, so they must be
 * rendered as valid Kotlin: non-finite and MIN_VALUE numbers as constants, keywords escaped.
 */
class ValidatorGeneratedCodeTest : AbstractValidationSymbolProcessorTest(), ValidatorModule {

    @Test
    fun fieldConstraintWithExtremeNumericValues() {
        compile0(
            listOf(ValidSymbolProcessorProvider()),
            """
            @Valid
            data class TestRecord(
                @field:Range(from = Double.NEGATIVE_INFINITY, to = Double.POSITIVE_INFINITY) val amount: Double,
                @field:Min(Long.MIN_VALUE) val min: Long
            )
            """.trimIndent()
        )
        compileResult.assertSuccess()

        @Suppress("UNCHECKED_CAST")
        val validator = newObject("\$TestRecord_Validator", rangeDoubleValidatorFactory(), minLongValidatorFactory()).objectInstance as Validator<Any>
        assertThat(validator.validate(new("TestRecord", 1e300, Long.MIN_VALUE))).isEmpty()
        assertThat(validator.validate(new("TestRecord", Double.NaN, Long.MIN_VALUE))).hasSize(1)
    }

    @Test
    fun argumentConstraintWithExtremeNumericValues() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
            @Component
            open class TestComponent {
                @Validate
                open fun test(@Range(from = 0.0, to = Double.POSITIVE_INFINITY) amount: Double, @Min(Long.MIN_VALUE) min: Long): Double = amount
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val component = newObject("\$TestComponent__AopProxy", rangeDoubleValidatorFactory(), minLongValidatorFactory())
        assertDoesNotThrow { component.invoke<Any>("test", 1e300, Long.MIN_VALUE) }
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test", -1.0, Long.MIN_VALUE) }
    }

    @Test
    fun fieldConstraintOnKeywordNamedProperty() {
        compile0(
            listOf(ValidSymbolProcessorProvider()),
            """
            @Valid
            data class TestRecord(@field:NotBlank val `object`: String, @field:NotBlank val `in`: String?)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        @Suppress("UNCHECKED_CAST")
        val validator = newObject("\$TestRecord_Validator", notBlankStringValidatorFactory()).objectInstance as Validator<Any>
        assertThat(validator.validate(new("TestRecord", "a", "b"))).isEmpty()
        assertThat(validator.validate(new("TestRecord", " ", " "))).hasSize(2)
    }

    @Test
    fun argumentConstraintOnKeywordNamedParameter() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
            @Component
            open class TestComponent {
                @Validate
                open fun test(@NotBlank `object`: String): String = `object`
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringValidatorFactory())
        assertThat(component.invoke<String>("test", "a")).isEqualTo("a")
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test", " ") }
    }
}

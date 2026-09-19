package io.koraframework.validation.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.validation.common.Validator
import io.koraframework.validation.common.constraint.ValidatorModule

/**
 * Constraint annotations may leave some of their members at the default value, so the generated
 * factory call must follow the declaration order of the annotation, not the order the members
 * happened to be written in.
 */
class ValidatorConstraintDefaultsTest : AbstractSymbolProcessorTest(), ValidatorModule {

    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.validation.common.annotation.*;
        """.trimIndent()
    }

    @Test
    fun sizeWithOnlyMaxSpecified() {
        compile0(
            listOf(ValidSymbolProcessorProvider()),
            """
            @Valid
            data class TestRecord(@Size(max = 5) val value: String)

            """.trimIndent()
        )
        compileResult.assertSuccess()

        @Suppress("UNCHECKED_CAST")
        val validator = loadClass("\$TestRecord_Validator")
            .constructors[0]
            .newInstance(sizeStringValidatorFactory()) as Validator<Any>
        val record = loadClass("TestRecord")

        assertThat(validator.validate(record.constructors[0].newInstance("abc"))).isEmpty()
        assertThat(validator.validate(record.constructors[0].newInstance("abcdef"))).hasSize(1)
    }
}

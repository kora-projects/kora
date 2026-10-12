package io.koraframework.validation.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.application.graph.TypeRef
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import io.koraframework.validation.common.Validator
import io.koraframework.validation.common.ViolationException
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
            import io.koraframework.common.annotation.Component;
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

    @Test
    fun validateArgumentAndResultOfStringTypealias() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
            typealias Name = String

            @Component
            open class TestComponent {
                @Validate
                @NotBlank
                open fun test(@NotBlank arg: Name, result: Name): Name = result
            }

            """.trimIndent()
        )
        compileResult.assertSuccess()

        val component = newObject("\$TestComponent__AopProxy", notBlankStringValidatorFactory())
        assertDoesNotThrow { component.invoke<Any>("test", "a", "b") }
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test", " ", "b") }
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test", "a", " ") }
    }

    @Test
    fun validateArgumentAndResultOfListTypealias() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
            typealias Ids = List<Long>

            @Component
            open class TestComponent {
                @Validate
                @Size(min = 1, max = 3)
                open fun test(@Size(min = 1, max = 3) arg: Ids, result: Ids): Ids = result
            }

            """.trimIndent()
        )
        compileResult.assertSuccess()

        val component = newObject("\$TestComponent__AopProxy", sizeListValidatorFactory(TypeRef.of(java.lang.Long::class.java)))
        assertDoesNotThrow { component.invoke<Any>("test", listOf(1L), listOf(2L)) }
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test", listOf<Long>(), listOf(2L)) }
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test", listOf(1L), listOf(1L, 2L, 3L, 4L)) }
    }

    @Test
    fun validateArgumentAndResultOfGenericListTypealias() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
            typealias Ids<T> = List<T>

            @Component
            open class TestComponent {
                @Validate
                @Size(min = 1, max = 3)
                open fun test(@Size(min = 1, max = 3) arg: Ids<Long>, result: Ids<Long>): Ids<Long> = result
            }

            """.trimIndent()
        )
        compileResult.assertSuccess()

        val component = newObject("\$TestComponent__AopProxy", sizeListValidatorFactory(TypeRef.of(java.lang.Long::class.java)))
        assertDoesNotThrow { component.invoke<Any>("test", listOf(1L), listOf(2L)) }
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test", listOf<Long>(), listOf(2L)) }
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test", listOf(1L), listOf(1L, 2L, 3L, 4L)) }
    }
}

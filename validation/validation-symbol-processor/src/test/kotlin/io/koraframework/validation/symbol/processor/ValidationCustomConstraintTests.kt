package io.koraframework.validation.symbol.processor

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.validation.common.Validator
import io.koraframework.validation.common.ViolationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Custom `@ValidatedBy` constraints: the factory type gets type arguments only when it declares type parameters,
 * and `create` arguments are literals of the declared annotation member types.
 */
class ValidationCustomConstraintTests : AbstractValidationSymbolProcessorTest() {

    override fun commonImports(): String = super.commonImports() + """

        import io.koraframework.validation.common.ValidatorFactory
        import kotlin.reflect.KClass

        """.trimIndent()

    private val nonGenericConstraint = """
        @ValidatedBy(MyValidFactory::class)
        annotation class MyValid

        class MyValidFactory : ValidatorFactory<String> {
            override fun create(): Validator<String> = Validator { value, context ->
                if (value == "bad") mutableListOf(context.violates("bad")) else mutableListOf()
            }
        }
        """.trimIndent()

    private val literalsConstraint = """
        @ValidatedBy(AllowedFactory::class)
        annotation class Allowed(val codes: IntArray, val f: Float, val c: Char, val k: KClass<*>)

        class AllowedFactory<T> : ValidatorFactory<T> {
            override fun create(): Validator<T> = throw UnsupportedOperationException()

            fun create(codes: IntArray, f: Float, c: Char, k: KClass<*>): Validator<T> {
                val attributesOk = f == 1.5f && c == 'x' && k == String::class
                return Validator { value, context ->
                    if (attributesOk && codes.any { it == value }) mutableListOf() else mutableListOf(context.violates("not allowed"))
                }
            }
        }
        """.trimIndent()

    @Test
    fun nonGenericFactoryOnField() {
        compile0(
            listOf(ValidSymbolProcessorProvider()),
            nonGenericConstraint,
            """
            @Valid
            data class TestRecord(@field:MyValid val value: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        @Suppress("UNCHECKED_CAST")
        val validator = newObject("\$TestRecord_Validator", new("MyValidFactory")).objectInstance as Validator<Any>
        assertEquals(0, validator.validate(new("TestRecord", "good")).size)
        assertEquals(1, validator.validate(new("TestRecord", "bad")).size)
    }

    @Test
    fun nonGenericFactoryOnValidateArgument() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            nonGenericConstraint,
            """
            @Component
            open class TestComponent {
                @Validate
                open fun test(@MyValid arg: String) { }
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val component = newObject("\$TestComponent__AopProxy", new("MyValidFactory"))
        assertDoesNotThrow { component.invoke<Any>("test", "good") }
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test", "bad") }
    }

    @Test
    fun customConstraintWithPrimitiveArrayFloatCharKClassOnField() {
        compile0(
            listOf(ValidSymbolProcessorProvider()),
            literalsConstraint,
            """
            @Valid
            data class TestRecord(@field:Allowed([200, 201], 1.5f, 'x', String::class) val code: Int)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        @Suppress("UNCHECKED_CAST")
        val validator = newObject("\$TestRecord_Validator", new("AllowedFactory")).objectInstance as Validator<Any>
        assertEquals(0, validator.validate(new("TestRecord", 201)).size)
        assertEquals(1, validator.validate(new("TestRecord", 500)).size)
    }

    @Test
    fun customConstraintWithPrimitiveArrayFloatCharKClassOnValidateArgument() {
        compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            literalsConstraint,
            """
            @Component
            open class TestComponent {
                @Validate
                open fun test(@Allowed([200, 201], 1.5f, 'x', String::class) code: Int) { }
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val component = newObject("\$TestComponent__AopProxy", new("AllowedFactory"))
        assertDoesNotThrow { component.invoke<Any>("test", 200) }
        assertThrows(ViolationException::class.java) { component.invoke<Any>("test", 500) }
    }
}

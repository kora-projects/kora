package io.koraframework.validation.symbol.processor

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ValidatorTypeResolutionTest : AbstractValidationSymbolProcessorTest() {

    private val processors = listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider())

    @Test
    fun validArgumentOfNestedClassType() {
        compile0(
            processors,
            """
            object Api {
                @Valid
                data class Req(@field:NotBlank val name: String)
            }
            """.trimIndent(),
            """
            @Component
            open class TestComponent {
                @Validate
                open fun test(@Valid req: Api.Req): String = req.name
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        assertThat(loadClass("\$TestComponent__AopProxy")).isNotNull()
    }

    @Test
    fun validResultOfNestedClassType() {
        compile0(
            processors,
            """
            class Api {
                @Valid
                data class Resp(@field:NotBlank val name: String)
            }
            """.trimIndent(),
            """
            @Component
            open class TestComponent {
                @Validate
                @Valid
                open fun test(): Api.Resp = Api.Resp("x")
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        assertThat(loadClass("\$TestComponent__AopProxy")).isNotNull()
    }

    @Test
    fun customConstraintOnNestedTypeField() {
        compile0(
            processors,
            """
            object Order {
                enum class Status { NEW, DONE }
            }
            """.trimIndent(),
            """
            import io.koraframework.validation.common.ValidatorFactory

            interface StatusFactory<T> : ValidatorFactory<T>

            @ValidatedBy(StatusFactory::class)
            @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
            annotation class NotDone
            """.trimIndent(),
            """
            @Valid
            data class Dto(@field:NotDone val status: Order.Status)
            """.trimIndent()
        )
        compileResult.assertSuccess()
        assertThat(loadClass("\$Dto_Validator")).isNotNull()
    }

    @Test
    fun genericDataClassValidator() {
        compile0(
            processors,
            """
            @Valid
            data class Item(@field:NotBlank val name: String)
            """.trimIndent(),
            """
            @Valid
            data class Page<T>(@field:Valid val items: List<T>, @field:Size(max = 3) val other: List<T>)
            """.trimIndent(),
            """
            @KoraApp
            interface TestApp : ValidatorModule {
                @Root
                fun root(v: Validator<Page<Item>>) = ""
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
        assertThat(loadClass("\$Page_Validator")).isNotNull()
        assertThat(loadClass("TestAppGraph")).isNotNull()
    }
}

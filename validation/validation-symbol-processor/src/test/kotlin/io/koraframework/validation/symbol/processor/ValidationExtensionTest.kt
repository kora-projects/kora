package io.koraframework.validation.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest

class ValidationExtensionTest : AbstractSymbolProcessorTest() {
    @Test
    fun testExtension() {
        compile0(listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                import io.koraframework.validation.common.annotation.Size
                import io.koraframework.validation.common.annotation.Valid

                @Valid
                data class TestRecord(@Size(min = 1, max = 5) val list: List<String>) {}
                
                """.trimIndent(),
            """
                import io.koraframework.common.annotation.KoraApp;
                import io.koraframework.common.annotation.Root;
                import io.koraframework.validation.common.Validator;
                import io.koraframework.validation.common.constraint.ValidatorModule;
                @KoraApp
                interface TestApp : ValidatorModule {
                   @Root
                   fun root(testRecordValidator: Validator<TestRecord>) = ""
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()
        val validatorClass = loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        val graph = loadClass("TestAppGraph")
        assertThat(graph).isNotNull()
    }

    @Test
    fun validateArgumentOfNestedClassType() {
        compile0(listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
                import io.koraframework.common.annotation.Component
                import io.koraframework.validation.common.annotation.Size
                import io.koraframework.validation.common.annotation.Valid
                import io.koraframework.validation.common.annotation.Validate

                @Component
                open class TestController {
                    @Validate
                    open fun submit(@Valid form: FormParam): String = form.name

                    @Valid
                    data class FormParam(@field:Size(min = 3, max = 10) val name: String)
                }

                """.trimIndent()
        )
        compileResult.assertSuccess()
        assertThat(loadClass("\$TestController_FormParam_Validator")).isNotNull()
        assertThat(loadClass("\$TestController__AopProxy")).isNotNull()
    }
}

package io.koraframework.validation.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest

class ValidatorSealedTypeTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.validation.common.annotation.*;
        """.trimIndent()
    }

    @Test
    fun testSealedInterface() {
        compile0(listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                @Valid
                sealed interface TestInterface {
                  @Valid
                  data class TestRecord(@Size(min = 1, max = 5) val list: List<String>): TestInterface
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()
        val validatorClass = loadClass("\$TestInterface_Validator")
        assertThat(validatorClass).isNotNull()
    }

    @Test
    fun testExtension() {
        compile0(listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                @Valid
                sealed interface TestInterface {
                  @Valid
                  data class TestRecord(@Size(min = 1, max = 5) val list: List<String>): TestInterface
                }
                
                """.trimIndent(),
            """
                import io.koraframework.common.KoraApp;
                import io.koraframework.common.annotation.Root;
                import io.koraframework.validation.common.Validator;
                import io.koraframework.validation.common.constraint.ValidatorModule;
                @KoraApp
                interface TestApp : ValidatorModule {
                   @Root
                   fun root(testRecordValidator: Validator<TestInterface>) = ""
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()
        val validatorClass = loadClass("\$TestInterface_Validator")
        assertThat(validatorClass).isNotNull()
        val graph = loadClass("TestAppGraph")
        assertThat(graph).isNotNull()
    }
}

package io.koraframework.validation.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
                import io.koraframework.common.KoraApp;
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
}

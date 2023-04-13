package ru.tinkoff.kora.validation.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest

class ValidationExtensionTest : AbstractSymbolProcessorTest() {
    @Test
    fun testExtension() {
        compile(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                import ru.tinkoff.kora.validation.common.annotation.Size
                import ru.tinkoff.kora.validation.common.annotation.Valid

                @Valid
                data class TestRecord(@Size(min = 1, max = 5) val list: List<String>) {}
                
                """.trimIndent(),
            """
                import ru.tinkoff.kora.common.KoraApp;
                import ru.tinkoff.kora.common.annotation.Root;
                import ru.tinkoff.kora.validation.common.Validator;
                import ru.tinkoff.kora.validation.common.constraint.ValidatorModule;
                @KoraApp
                interface TestApp : ValidatorModule {
                   @Root
                   fun root(testRecordValidator: Validator<TestRecord>) = ""
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()
        val validatorClass = compileResult.loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        val graph = compileResult.loadClass("TestAppGraph")
        assertThat(graph).isNotNull()
    }

    @Test
    fun testExtensionNoAnnotationProcessor() {
        compile(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                import ru.tinkoff.kora.validation.common.annotation.Size

                data class TestRecord(@Size(min = 1, max = 5) val list: List<String>) {}
                """.trimIndent(),
            """
                import ru.tinkoff.kora.common.KoraApp
                import ru.tinkoff.kora.common.annotation.Root
                import ru.tinkoff.kora.validation.common.Validator
                import ru.tinkoff.kora.validation.common.constraint.ValidatorModule

                @KoraApp
                public interface TestApp : ValidatorModule {
                   @Root
                   fun root(testRecordValidator: Validator<TestRecord>) = ""
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()
        val validatorClass = compileResult.loadClass("\$TestRecord_Validator")
        assertThat(validatorClass).isNotNull()
        val graph = compileResult.loadClass("TestAppGraph")
        assertThat(graph).isNotNull()
    }
}

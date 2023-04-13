package ru.tinkoff.kora.validation.symbol.processor

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest

class ValidatorSealedTypeTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.validation.common.annotation.*;
        """.trimIndent()
    }

    @Test
    fun testSealedInterface() {
        compile(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                @Valid
                sealed interface TestInterface {
                  @Valid
                  data class TestRecord(@Size(min = 1, max = 5) val list: List<String>): TestInterface
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()
        val validatorClass = compileResult.loadClass("\$TestInterface_Validator")
        assertThat(validatorClass).isNotNull()
    }

    @Test
    fun testExtension() {
        compile(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                @Valid
                sealed interface TestInterface {
                  @Valid
                  data class TestRecord(@Size(min = 1, max = 5) val list: List<String>): TestInterface
                }
                
                """.trimIndent(),
            """
                import ru.tinkoff.kora.common.KoraApp;
                import ru.tinkoff.kora.common.annotation.Root;
                import ru.tinkoff.kora.validation.common.Validator;
                import ru.tinkoff.kora.validation.common.constraint.ValidatorModule;
                @KoraApp
                interface TestApp : ValidatorModule {
                   @Root
                   fun root(testRecordValidator: Validator<TestInterface>) = ""
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()
        val validatorClass = compileResult.loadClass("\$TestInterface_Validator")
        assertThat(validatorClass).isNotNull()
        val graph = compileResult.loadClass("TestAppGraph")
        assertThat(graph).isNotNull()
    }

    @Test
    fun testExtensionNoAnnotationProcessor() {
        compile(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider()),
            """
                sealed interface TestInterface {
                  data class TestRecord(@Size(min = 1, max = 5) val list: List<String>): TestInterface
                }
                
                """.trimIndent(),
            """
                import ru.tinkoff.kora.common.KoraApp
                import ru.tinkoff.kora.common.annotation.Root
                import ru.tinkoff.kora.validation.common.Validator
                import ru.tinkoff.kora.validation.common.constraint.ValidatorModule

                @KoraApp
                public interface TestApp : ValidatorModule {
                   @Root
                   fun root(testRecordValidator: Validator<TestInterface>) = ""
                }
                
                """.trimIndent()
        )
        compileResult.assertSuccess()
        val validatorClass = compileResult.loadClass("\$TestInterface_Validator")
        assertThat(validatorClass).isNotNull()
        val graph = compileResult.loadClass("TestAppGraph")
        assertThat(graph).isNotNull()
    }
}

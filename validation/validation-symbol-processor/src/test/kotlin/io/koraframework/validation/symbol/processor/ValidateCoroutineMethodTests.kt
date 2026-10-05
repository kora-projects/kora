package io.koraframework.validation.symbol.processor

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ValidateCoroutineMethodTests : AbstractValidationSymbolProcessorTest() {

    @Test
    fun validateSuspendIsRejected() {
        val result = compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
            @Component
            open class TestComponent {
                @Validate
                open suspend fun test(@NotBlank arg: String): String = arg
            }
            """.trimIndent()
        ).assertFailure()

        assertThat(result.messages).anySatisfy {
            assertThat(it)
                .contains("TestComponent#test")
                .contains("Suspend methods are not supported by Kora aspects (@Validate")
                .contains("StructuredTaskScope.open")
                .contains("Remove suspend from the method")
        }
    }

    @Test
    fun validateFlowIsRejected() {
        val result = compile0(
            listOf(KoraAppProcessorProvider(), ValidSymbolProcessorProvider(), AopSymbolProcessorProvider()),
            """
            @Component
            open class TestComponent {
                @Validate
                open fun test(@NotBlank arg: String): kotlinx.coroutines.flow.Flow<String> = throw UnsupportedOperationException()
            }
            """.trimIndent()
        ).assertFailure()

        assertThat(result.messages).anySatisfy {
            assertThat(it)
                .contains("TestComponent#test")
                .contains("Methods returning kotlinx.coroutines.flow.Flow are not supported by Kora aspects (@Validate")
                .contains("StructuredTaskScope.open")
        }
    }
}

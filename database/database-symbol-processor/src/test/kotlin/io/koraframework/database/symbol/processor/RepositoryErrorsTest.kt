package io.koraframework.database.symbol.processor

import org.assertj.core.api.Assertions
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import io.koraframework.database.symbol.processor.repository.error.InvalidParameterUsage
import io.koraframework.ksp.common.CompilationErrorException
import io.koraframework.ksp.common.symbolProcess
import kotlin.reflect.KClass

class RepositoryErrorsTest {
    @Test
    fun testParameterUsage() {
        Assertions.assertThatThrownBy { process(InvalidParameterUsage::class) }
            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e: CompilationErrorException ->
                SoftAssertions.assertSoftly { s: SoftAssertions ->
                    s.assertThat(e.messages).anyMatch { it.contains("Parameter usage wasn't found in sql: param2") }
                }
            }
    }

    fun <T: Any> process(repository: KClass<T>) {
        symbolProcess(listOf(RepositorySymbolProcessorProvider()), listOf(repository))
    }
}

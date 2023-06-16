package ru.tinkoff.kora.database.symbol.processor

import org.assertj.core.api.Assertions
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.database.symbol.processor.repository.error.InvalidParameterUsage
import ru.tinkoff.kora.ksp.common.CompilationErrorException
import ru.tinkoff.kora.ksp.common.symbolProcess
import kotlin.reflect.KClass

class RepositoryErrorsTest {
    @Test
    fun testParameterUsage() {
        Assertions.assertThatThrownBy { process(InvalidParameterUsage::class) }
            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e: CompilationErrorException ->
                SoftAssertions.assertSoftly { s: SoftAssertions ->
                    s.assertThat(e.messages).anyMatch { it.contains("Parameter usage was not found in sql: param2") }
                }
            }
    }

    fun <T: Any> process(repository: KClass<T>) {
        symbolProcess(repository, RepositorySymbolProcessorProvider())
    }
}

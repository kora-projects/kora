package io.koraframework.database.symbol.processor

import io.koraframework.database.symbol.processor.repository.error.InvalidParameterUsage
import io.koraframework.database.symbol.processor.repository.error.QuotedQueryPlaceholder
import io.koraframework.database.symbol.processor.repository.error.UnknownEntityField
import io.koraframework.database.symbol.processor.repository.error.UnknownQueryParameter
import io.koraframework.ksp.common.CompilationErrorException
import io.koraframework.ksp.common.symbolProcess
import org.assertj.core.api.Assertions
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

class RepositoryErrorsTest {
    @Test
    fun testParameterUsage() {
        Assertions.assertThatThrownBy { process(InvalidParameterUsage::class) }
            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e: CompilationErrorException ->
                SoftAssertions.assertSoftly { s: SoftAssertions ->
                    s.assertThat(e.messages).anySatisfy {
                        s.assertThat(it)
                            .contains("Query parameter is unused")
                            .contains("param2")
                            .contains("Problem:")
                            .contains("Fix:")
                    }
                }
            }
    }

    @Test
    fun testUnknownQueryParameter() {
        Assertions.assertThatThrownBy { process(UnknownQueryParameter::class) }
            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e: CompilationErrorException ->
                SoftAssertions.assertSoftly { s: SoftAssertions ->
                    s.assertThat(e.messages).anySatisfy {
                        s.assertThat(it)
                            .contains("SQL query placeholder has no matching method parameter")
                            .contains(":userId")
                            .contains("Available parameters:")
                            .contains(":id")
                            .contains("Problem:")
                            .contains("Fix:")
                    }
                }
            }
    }

    @Test
    fun testUnknownEntityField() {
        Assertions.assertThatThrownBy { process(UnknownEntityField::class) }
            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e: CompilationErrorException ->
                SoftAssertions.assertSoftly { s: SoftAssertions ->
                    s.assertThat(e.messages).anySatisfy {
                        s.assertThat(it)
                            .contains("SQL query placeholder has no matching entity field")
                            .contains(":dto.name")
                            .contains("Available fields for parameter 'dto':")
                            .contains(":dto.id")
                            .contains("Problem:")
                            .contains("Fix:")
                    }
                }
            }
    }

    @Test
    fun testQuotedQueryPlaceholderIsIgnored() {
        process(QuotedQueryPlaceholder::class)
    }

    fun <T: Any> process(repository: KClass<T>) {
        symbolProcess(listOf(RepositorySymbolProcessorProvider()), listOf(repository))
    }
}

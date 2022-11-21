package ru.tinkoff.kora.database.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import ru.tinkoff.kora.database.symbol.processor.jdbc.MockJdbcExecutor
import ru.tinkoff.kora.database.symbol.processor.repository.QueryFromResourceRepository
import ru.tinkoff.kora.ksp.common.symbolProcessFiles

class QueryFromResourceTest {
    private val executor: MockJdbcExecutor = MockJdbcExecutor()

    @KspExperimental
    @Test
    fun testNativeParameter() {
        val cl = symbolProcessFiles(
            listOf(
                "src/test/kotlin/ru/tinkoff/kora/database/symbol/processor/repository/QueryFromResourceRepository.kt",
            ), listOf<SymbolProcessorProvider>(RepositorySymbolProcessorProvider())
        )
        val repository = cl.loadClass("ru.tinkoff.kora.database.symbol.processor.repository.\$QueryFromResourceRepository_Impl")
            .constructors[0]
            .newInstance(executor) as QueryFromResourceRepository
        repository.test()

        Mockito.verify(executor.mockConnection).prepareStatement("SELECT 1;\n")
    }
}

package io.koraframework.database.symbol.processor.jdbc

import io.koraframework.database.symbol.processor.RepositorySymbolProcessorProvider
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import org.junit.jupiter.api.Test

class JdbcExtensionTests : AbstractJdbcRepositoryTest() {
    @Test
    fun testAnnotatedComponentsFound() {
        compile0(
            listOf(KoraAppProcessorProvider(), JdbcEntitySymbolProcessorProvider()),
            """
            @KoraApp
            interface Application : JdbcDatabaseModule {
                @Root fun testRowMapper(m1: JdbcRowMapper<TestRow>) = ""
                @Root fun testResultSetMapper(m1: JdbcResultSetMapper<TestRow>) = ""
                @Root fun testListResultSetMapper(m1: JdbcResultSetMapper<List<TestRow>>) = ""
            }
            """.trimIndent(),
            """
            @io.koraframework.database.jdbc.annotation.EntityJdbc
            data class TestRow(val id: Int)
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }

    @Test
    fun testRepositoryReturnTypealias() {
        compile0(
            listOf(KoraAppProcessorProvider(), JdbcEntitySymbolProcessorProvider(), RepositorySymbolProcessorProvider()),
            """
            @KoraApp
            interface Application {
                @Root fun root(repo: TestRepository) = ""

                fun jdbcExecutor(): JdbcExecutor = org.mockito.Mockito.mock(JdbcExecutor::class.java)
            }
            """.trimIndent(),
            """
            typealias RowAlias = TestRow
            typealias Rows = List<TestRow>

            @io.koraframework.database.jdbc.annotation.EntityJdbc
            data class TestRow(val id: Int)

            @Repository
            interface TestRepository : JdbcRepository {
                @Query("SELECT id FROM test WHERE id = :id")
                fun findOne(id: Int): RowAlias?

                @Query("SELECT id FROM test")
                fun findAll(): Rows
            }
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }
}

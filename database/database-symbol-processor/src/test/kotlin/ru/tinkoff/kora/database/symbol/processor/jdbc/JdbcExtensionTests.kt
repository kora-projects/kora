package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.junit.jupiter.api.Test
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider

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
            @ru.tinkoff.kora.database.jdbc.EntityJdbc
            data class TestRow(val id: Int)
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }
}

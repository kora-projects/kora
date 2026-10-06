package io.koraframework.database.symbol.processor.jdbc

import io.koraframework.database.jdbc.mapper.result.JdbcResultSetMapper
import io.koraframework.kora.app.ksp.KoraAppProcessorProvider
import io.koraframework.ksp.common.GraphUtil.toGraph
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.sql.ResultSet

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
    fun testListOfScalarResultSetMapperNullability() {
        compile0(
            listOf(KoraAppProcessorProvider()),
            """
            @KoraApp
            interface Application {
                fun intRowMapper(): JdbcRowMapper<Int> = JdbcRowMapper { rs -> rs.getObject(1) as Int? }
                @Root fun nonNull(m: JdbcResultSetMapper<List<Int>>) = NonNullHolder(m)
                @Root fun nullable(m: JdbcResultSetMapper<List<Int?>>) = NullableHolder(m)
            }
            """.trimIndent(),
            """
            class NonNullHolder(val mapper: JdbcResultSetMapper<List<Int>>)
            """.trimIndent(),
            """
            class NullableHolder(val mapper: JdbcResultSetMapper<List<Int?>>)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        loadClass("ApplicationGraph").toGraph().use { graph ->
            fun mapper(holder: String): JdbcResultSetMapper<*> {
                val holderClass = loadClass(holder)
                val instance = graph.findByType(holderClass)!!
                return holderClass.getMethod("getMapper").invoke(instance) as JdbcResultSetMapper<*>
            }

            fun resultSet(): ResultSet {
                val rs = Mockito.mock(ResultSet::class.java)
                whenever(rs.next()).thenReturn(true, true, false)
                whenever(rs.getObject(1)).thenReturn(null, 5)
                return rs
            }

            Assertions.assertThat(mapper("NullableHolder").apply(resultSet()) as List<*>).containsExactly(null, 5)
            Assertions.assertThatThrownBy { mapper("NonNullHolder").apply(resultSet()) }
                .isInstanceOf(NullPointerException::class.java)
                .hasMessage("Result mapping is expected non-null, but was null")
        }
    }
}

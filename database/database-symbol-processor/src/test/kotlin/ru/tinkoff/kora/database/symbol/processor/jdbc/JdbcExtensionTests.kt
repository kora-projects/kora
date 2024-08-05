package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.ksp.common.GraphUtil.toGraphDraw

class JdbcExtensionTests : AbstractJdbcRepositoryTest() {

    @Test
    fun testRowMapper() {
        val result = compile0(
            """
            @KoraApp
            interface Application : JdbcDatabaseModule {
            
                @Root
                fun testRowMapper(m1: JdbcResultSetMapper<TestRow>) = ""
                
                @Tag(String::class)
                fun taggedMapper(): JdbcResultColumnMapper<String> = JdbcResultColumnMapper { row, index -> row.getString(index) }
            }
            """.trimIndent(),
            """
            data class TestRow(val f1: String, val f2: String, @field:Tag(String::class) val f3: String, @field:Mapping(TestRowResultColumnMapper::class) val f4: String)
            """.trimIndent(),
            """
            class TestRowResultColumnMapper : JdbcResultColumnMapper<String> {
                override fun apply(row: ResultSet?, index: Int): String = row!!.getString(index)
            }
            """.trimIndent()
        )

        compileResult.assertSuccess()
        val graph = compileResult.loadClass("ApplicationGraph").toGraphDraw()
        Assertions.assertThat(graph.nodes).hasSize(4)

        val mapper = compileResult.loadClass("\$TestRow_JdbcRowMapper")
        val constructor = mapper.constructors.first()
        Assertions.assertThat(constructor.parameters).hasSize(1)

        Assertions.assertThat(constructor.parameters.first().annotations).hasSize(1)
        Assertions.assertThat(constructor.parameters.first().annotations[0]).isInstanceOf(Tag::class.java)
    }
}

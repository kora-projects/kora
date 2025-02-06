package ru.tinkoff.kora.database.symbol.processor.cassandra

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest

class CassandraEntitySymbolProcessorTest : AbstractSymbolProcessorTest() {
    @Test
    fun testMappersGenerated() {
        compile0(listOf(CassandraEntitySymbolProcessorProvider()),
            """
            @ru.tinkoff.kora.database.cassandra.annotation.EntityCassandra
            data class TestRow(val id: Int)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        assertThat(loadClass("\$TestRow_CassandraRowMapper").getConstructor().newInstance()).isInstanceOf(CassandraRowMapper::class.java)
        assertThat(loadClass("\$TestRow_ListCassandraResultSetMapper").getConstructor().newInstance()).isInstanceOf(CassandraResultSetMapper::class.java)
    }
}

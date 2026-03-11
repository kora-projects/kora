package io.koraframework.database.symbol.processor.jdbc.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import io.koraframework.database.jdbc.mapper.result.JdbcResultSetMapper
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper
import io.koraframework.database.symbol.processor.jdbc.JdbcEntitySymbolProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest

class JdbcEntitySymbolProcessorTest : AbstractSymbolProcessorTest() {
    @Test
    fun testMappersGenerated() {
        compile0(listOf(JdbcEntitySymbolProcessorProvider()),
            """
            @io.koraframework.database.jdbc.EntityJdbc
            data class TestRow(val id: Int)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        assertThat(loadClass("\$TestRow_JdbcResultSetMapper").getConstructor().newInstance()).isInstanceOf(JdbcResultSetMapper::class.java)
        assertThat(loadClass("\$TestRow_ListJdbcResultSetMapper").getConstructor().newInstance()).isInstanceOf(JdbcResultSetMapper::class.java)
        assertThat(loadClass("\$TestRow_JdbcRowMapper").getConstructor().newInstance()).isInstanceOf(JdbcRowMapper::class.java)
    }
}

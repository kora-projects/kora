package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper
import java.sql.ResultSet

class JdbcMapperTests : AbstractJdbcRepositoryTest() {

    @Test
    fun testRowMapperGenerated() {
        compile0(
            listOf(JdbcEntitySymbolProcessorProvider()),
            """
            @EntityJdbc
            data class TestRow(val f1: String, @field:Column("some_f2") val f2: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val mapper = newGenerated("\$TestRow_JdbcRowMapper").invoke() as JdbcRowMapper<*>
        assertThat(mapper).isInstanceOf(JdbcRowMapper::class.java)

        val rs = mock<ResultSet>()
        whenever(rs.findColumn("f1")).thenReturn(1)
        whenever(rs.findColumn("some_f2")).thenReturn(2)
        whenever(rs.getString(1)).thenReturn("test1")
        whenever(rs.getString(2)).thenReturn("test2")
        val expected = newObject("TestRow", "test1", "test2").objectInstance

        val o1 = mapper.apply(rs)
        assertThat(o1).isEqualTo(expected)
        verify(rs).getString(1)
        verify(rs).getString(2)
    }

    @Test
    fun testResultSetMapperGenerated() {
        compile0(
            listOf(JdbcEntitySymbolProcessorProvider()),
            """
            @EntityJdbc
            data class TestRow(val f1: String, @field:Column("some_f2") val f2: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val mapper = newGenerated("\$TestRow_JdbcResultSetMapper").invoke() as JdbcResultSetMapper<*>
        assertThat(mapper).isInstanceOf(JdbcResultSetMapper::class.java)

        val rs = mock<ResultSet>()
        whenever(rs.next()).thenReturn(true, false)
        whenever(rs.findColumn("f1")).thenReturn(1)
        whenever(rs.findColumn("some_f2")).thenReturn(2)
        whenever(rs.getString(1)).thenReturn("test1")
        whenever(rs.getString(2)).thenReturn("test2")
        val expected = newObject("TestRow", "test1", "test2").objectInstance

        val o1 = mapper.apply(rs)
        assertThat(o1).isEqualTo(expected)
        verify(rs).getString(1)
        verify(rs).getString(2)
    }

    @Test
    fun testListResultSetMapperGenerated() {
        compile0(
            listOf(JdbcEntitySymbolProcessorProvider()),
            """
            @EntityJdbc
            data class TestRow(val f1: String, @field:Column("some_f2") val f2: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val mapper = newGenerated("\$TestRow_ListJdbcResultSetMapper").invoke() as JdbcResultSetMapper<*>
        assertThat(mapper).isInstanceOf(JdbcResultSetMapper::class.java)

        val rs = mock<ResultSet>()
        whenever(rs.next()).thenReturn(true, true, false)
        whenever(rs.findColumn("f1")).thenReturn(1)
        whenever(rs.findColumn("some_f2")).thenReturn(2)
        whenever(rs.getString(1)).thenReturn("test1")
        whenever(rs.getString(2)).thenReturn("test2")
        val expected1 = newObject("TestRow", "test1", "test2").objectInstance
        val expected2 = newObject("TestRow", "test1", "test2").objectInstance
        val expected = listOf(expected1, expected2)

        val o1 = mapper.apply(rs)
        assertThat(o1).isEqualTo(expected)
        verify(rs, times(3)).next()
        verify(rs, times(2)).getString(1)
        verify(rs, times(2)).getString(2)
        reset(rs)

        whenever(rs.next()).thenReturn(false)
        val o2 = mapper.apply(rs)
        assertThat(o2 as List<*>).isEmpty()
        verify(rs).next()
        verifyNoMoreInteractions(rs)
    }
}

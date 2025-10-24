package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.datastax.oss.driver.api.core.cql.ColumnDefinitions
import com.datastax.oss.driver.api.core.cql.ResultSet
import com.datastax.oss.driver.api.core.cql.Row
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper
import ru.tinkoff.kora.ksp.common.KotlinCompilation

class CassandraMapperTests : AbstractCassandraRepositoryTest() {

    @Test
    fun testRowMapperGenerated() {
        KotlinCompilation().withClasspathJar("java-driver-core").compile(
            listOf(CassandraEntitySymbolProcessorProvider()),
            """
            @EntityCassandra
            data class TestRow(val f1: String, @field:Column("some_f2") val f2: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val mapper = newGenerated("\$TestRow_CassandraRowMapper").invoke() as CassandraRowMapper<*>
        assertThat(mapper).isInstanceOf(CassandraRowMapper::class.java)

        val row = mock<Row>()
        val cd = mock<ColumnDefinitions>()
        whenever(row.columnDefinitions).thenReturn(cd)
        whenever(cd.firstIndexOf("f1")).thenReturn(1)
        whenever(cd.firstIndexOf("some_f2")).thenReturn(2)
        whenever(row.getString(1)).thenReturn("test1")
        whenever(row.getString(2)).thenReturn("test2")
        val expected = newObject("TestRow", "test1", "test2").objectInstance

        val o1 = mapper.apply(row)
        assertThat(o1).isEqualTo(expected)
        verify(row).getString(1)
        verify(row).getString(2)
    }

    @Test
    fun testResultSetMapperGenerated() {
        KotlinCompilation().withClasspathJar("java-driver-core").compile(
            listOf(CassandraEntitySymbolProcessorProvider()),
            """
            @EntityCassandra
            data class TestRow(val f1: String, @field:Column("some_f2") val f2: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val mapper = newGenerated("\$TestRow_CassandraResultSetMapper").invoke() as CassandraResultSetMapper<*>
        assertThat(mapper).isInstanceOf(CassandraResultSetMapper::class.java)

        val rs = mock<ResultSet>()
        val it = mock<MutableIterator<Row>>()
        whenever(rs.iterator()).thenReturn(it)
        whenever(it.hasNext()).thenReturn(true, false)
        val row = mock<Row>()
        val cd = mock<ColumnDefinitions>()
        whenever(rs.columnDefinitions).thenReturn(cd)
        whenever(it.next()).thenReturn(row)
        whenever(cd.firstIndexOf("f1")).thenReturn(1)
        whenever(cd.firstIndexOf("some_f2")).thenReturn(2)
        whenever(row.getString(1)).thenReturn("test1")
        whenever(row.getString(2)).thenReturn("test2")
        val expected = newObject("TestRow", "test1", "test2").objectInstance

        val o1 = mapper.apply(rs)
        assertThat(o1).isEqualTo(expected)
        verify(row).getString(1)
        verify(row).getString(2)
    }

    @Test
    fun testListResultSetMapperGenerated() {
        KotlinCompilation().withClasspathJar("java-driver-core").compile(
            listOf(CassandraEntitySymbolProcessorProvider()),
            """
            @EntityCassandra
            data class TestRow(val f1: String, @field:Column("some_f2") val f2: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val mapper = newGenerated("\$TestRow_ListCassandraResultSetMapper").invoke() as CassandraResultSetMapper<*>
        assertThat(mapper).isInstanceOf(CassandraResultSetMapper::class.java)

        val rs = mock<ResultSet>()
        val it = mock<MutableIterator<Row>>()
        whenever(rs.iterator()).thenReturn(it)
        whenever(rs.availableWithoutFetching).thenReturn(2)
        whenever(it.hasNext()).thenReturn(true, true, true, false)
        val row = mock<Row>()
        val cd = mock<ColumnDefinitions>()
        whenever(rs.columnDefinitions).thenReturn(cd)
        whenever(it.next()).thenReturn(row, row)
        whenever(cd.firstIndexOf("f1")).thenReturn(1)
        whenever(cd.firstIndexOf("some_f2")).thenReturn(2)
        whenever(row.getString(1)).thenReturn("test1")
        whenever(row.getString(2)).thenReturn("test2")
        val expected1 = newObject("TestRow", "test1", "test2").objectInstance
        val expected2 = newObject("TestRow", "test1", "test2").objectInstance
        val expected = listOf(expected1, expected2)

        val o1 = mapper.apply(rs)
        assertThat(o1).isEqualTo(expected)
        verify(it, times(2)).next()
        verify(row, times(2)).getString(1)
        verify(row, times(2)).getString(2)
        reset(rs)
        reset(it)

        whenever(rs.iterator()).thenReturn(it)
        whenever(it.hasNext()).thenReturn(false)
        val o2 = mapper.apply(rs)
        assertThat(o2 as List<*>).isEmpty()

        verify(rs).iterator()
        verify(it).hasNext()
        verifyNoMoreInteractions(rs)
        verifyNoMoreInteractions(it)
    }
}

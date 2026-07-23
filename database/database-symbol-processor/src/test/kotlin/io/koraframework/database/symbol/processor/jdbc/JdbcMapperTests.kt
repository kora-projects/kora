package io.koraframework.database.symbol.processor.jdbc

import io.koraframework.database.jdbc.mapper.result.JdbcResultSetMapper
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.sql.ResultSet

class JdbcMapperTests : AbstractJdbcRepositoryTest() {

    @Test
    fun testOneToManyListResultSetMapperGenerated() {
        compile0(
            listOf(JdbcEntitySymbolProcessorProvider()),
            """
            @EntityJdbc
            data class UserOrdersView(@field:Embedded("u_") val user: User, @field:Embedded("o_") val orders: List<Order>)

            @Table("users")
            data class User(@field:Id val id: String, val name: String)

            @Table("orders")
            data class Order(@field:Id val id: String, @field:Column("user_id") val userId: String, val number: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val mapper = newGenerated("\$UserOrdersView_ListJdbcResultSetMapper").invoke() as JdbcResultSetMapper<*>
        val rs = mock<ResultSet>()
        whenever(rs.next()).thenReturn(true, true, false)
        whenever(rs.findColumn("u_id")).thenReturn(1)
        whenever(rs.findColumn("u_name")).thenReturn(2)
        whenever(rs.findColumn("o_id")).thenReturn(3)
        whenever(rs.findColumn("o_user_id")).thenReturn(4)
        whenever(rs.findColumn("o_number")).thenReturn(5)
        whenever(rs.getString(1)).thenReturn("u1", "u1")
        whenever(rs.getString(2)).thenReturn("User 1", "User 1")
        whenever(rs.getString(3)).thenReturn("o1", "o2")
        whenever(rs.getString(4)).thenReturn("u1", "u1")
        whenever(rs.getString(5)).thenReturn("n1", "n2")
        whenever(rs.wasNull()).thenReturn(false, false, false, false, false, false, false, false, false, false)

        val result = mapper.apply(rs) as List<*>

        assertThat(result).hasSize(1)
        val orders = result[0]!!.javaClass.getMethod("getOrders").invoke(result[0]) as List<*>
        assertThat(orders).hasSize(2)
    }

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

package io.koraframework.database.symbol.processor.jdbc

import io.koraframework.database.jdbc.mapper.result.JdbcResultSetMapper
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
            data class Order(@field:Id val id: Long, @field:Column("user_id") val userId: String, val number: String?)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val mapper = newGenerated("\$UserOrdersView_ListJdbcResultSetMapper").invoke() as JdbcResultSetMapper<*>
        val rs = mock<ResultSet>()
        whenever(rs.next()).thenReturn(true, true, true, false)
        whenever(rs.findColumn("u_id")).thenReturn(1)
        whenever(rs.findColumn("u_name")).thenReturn(2)
        whenever(rs.findColumn("o_id")).thenReturn(3)
        whenever(rs.findColumn("o_user_id")).thenReturn(4)
        whenever(rs.findColumn("o_number")).thenReturn(5)
        whenever(rs.getString(1)).thenReturn("u1", "u2", "u2")
        whenever(rs.getString(2)).thenReturn("User 1", "User 2", "User 2")
        whenever(rs.getLong(3)).thenReturn(0L, 1L, 2L)
        whenever(rs.getString(4)).thenReturn(null, "u2", "u2")
        whenever(rs.getString(5)).thenReturn(null, null, "n2")
        whenever(rs.wasNull()).thenReturn(
            false, false, true, true, true,
            false, false, false, false, true,
            false, false, false, false, false
        )

        val result = mapper.apply(rs) as List<*>

        assertThat(result).hasSize(2)
        val orders = result[0]!!.javaClass.getMethod("getOrders").invoke(result[0]) as List<*>
        assertThat(orders).isEmpty()
        val secondOrders = result[1]!!.javaClass.getMethod("getOrders").invoke(result[1]) as List<*>
        assertThat(secondOrders).hasSize(2)
        assertThat(secondOrders[0]!!.javaClass.getMethod("getNumber").invoke(secondOrders[0])).isNull()
        assertThat(secondOrders[1]!!.javaClass.getMethod("getNumber").invoke(secondOrders[1])).isEqualTo("n2")
    }

    @Test
    fun testOneToManyResultSetMapperAggregatesRows() {
        compileUserOrdersView()

        val mapper = newGenerated("\$UserOrdersView_JdbcResultSetMapper").invoke() as JdbcResultSetMapper<*>
        val rs = userOrdersResultSet("u1", "u1")

        val result = mapper.apply(rs)

        assertThat(result).isNotNull()
        val orders = result!!.javaClass.getMethod("getOrders").invoke(result) as List<*>
        assertThat(orders).hasSize(2)

        val emptyRs = mock<ResultSet>()
        whenever(emptyRs.next()).thenReturn(false)
        assertThat(mapper.apply(emptyRs)).isNull()
    }

    @Test
    fun testOneToManyResultSetMapperRejectsSeveralRoots() {
        compileUserOrdersView()

        val mapper = newGenerated("\$UserOrdersView_JdbcResultSetMapper").invoke() as JdbcResultSetMapper<*>
        val rs = userOrdersResultSet("u1", "u2")

        assertThatThrownBy { mapper.apply(rs) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("ResultSet was expected to return zero or one root entity but got two or more")
    }

    private fun compileUserOrdersView() {
        compile0(
            listOf(JdbcEntitySymbolProcessorProvider()),
            """
            @EntityJdbc
            data class UserOrdersView(@field:Embedded("u_") val user: User, @field:Embedded("o_") val orders: List<Order>)

            @Table("users")
            data class User(@field:Id val id: String, val name: String)

            @Table("orders")
            data class Order(@field:Id val id: Long, @field:Column("user_id") val userId: String, val number: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()
    }

    private fun userOrdersResultSet(firstUserId: String, secondUserId: String): ResultSet {
        val rs = mock<ResultSet>()
        whenever(rs.next()).thenReturn(true, true, false)
        whenever(rs.findColumn("u_id")).thenReturn(1)
        whenever(rs.findColumn("u_name")).thenReturn(2)
        whenever(rs.findColumn("o_id")).thenReturn(3)
        whenever(rs.findColumn("o_user_id")).thenReturn(4)
        whenever(rs.findColumn("o_number")).thenReturn(5)
        whenever(rs.getString(1)).thenReturn(firstUserId, secondUserId)
        whenever(rs.getString(2)).thenReturn("User")
        whenever(rs.getLong(3)).thenReturn(1L, 2L)
        whenever(rs.getString(4)).thenReturn(firstUserId, secondUserId)
        whenever(rs.getString(5)).thenReturn("n1", "n2")
        whenever(rs.wasNull()).thenReturn(false)
        return rs
    }

    @Test
    fun testColumnMapperValueForSqlNullKeepsAbsentEmbeddedDetection() {
        compile0(
            listOf(JdbcEntitySymbolProcessorProvider()),
            """
            class TagsMapper : JdbcResultColumnMapper<List<String>> {
                override fun apply(rs: ResultSet, index: Int): List<String> {
                    val array = rs.getArray(index)
                    return if (array == null) listOf() else (array.array as Array<String>).toList()
                }
            }

            @EntityJdbc
            data class UserOrdersView(@field:Embedded("u_") val user: User, @field:Embedded("o_") val orders: List<Order>, @field:Embedded("t_") val tagged: Tagged?)

            @Table("users")
            data class User(@field:Id val id: String, val name: String)

            @Table("orders")
            data class Order(@field:Id val id: Long, @Mapping(TagsMapper::class) val tags: List<String>)

            data class Tagged(@Mapping(TagsMapper::class) val tags: List<String>)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val mapper = newGenerated("\$UserOrdersView_ListJdbcResultSetMapper").invoke() as JdbcResultSetMapper<*>
        val rs = mock<ResultSet>()
        whenever(rs.next()).thenReturn(true, false)
        whenever(rs.findColumn("u_id")).thenReturn(1)
        whenever(rs.findColumn("u_name")).thenReturn(2)
        whenever(rs.findColumn("o_id")).thenReturn(3)
        whenever(rs.findColumn("o_tags")).thenReturn(4)
        whenever(rs.findColumn("t_tags")).thenReturn(5)
        whenever(rs.getString(1)).thenReturn("u1")
        whenever(rs.getString(2)).thenReturn("User")
        // LEFT JOIN without a child row and an absent nullable embedded: every o_* and t_* column is NULL
        whenever(rs.wasNull()).thenReturn(false, false, true, true, true)

        val result = mapper.apply(rs)

        assertThat(result).hasToString("[UserOrdersView(user=User(id=u1, name=User), orders=[], tagged=null)]")
    }

    @Test
    fun testColumnMapperValueForSqlNullIsKept() {
        compile0(
            listOf(JdbcEntitySymbolProcessorProvider()),
            """
            class TagsMapper : JdbcResultColumnMapper<List<String>> {
                override fun apply(rs: ResultSet, index: Int): List<String> {
                    val array = rs.getArray(index)
                    return if (array == null) listOf() else (array.array as Array<String>).toList()
                }
            }

            @EntityJdbc
            data class TestEntity(val id: Long, @Mapping(TagsMapper::class) val tags: List<String>, @Mapping(TagsMapper::class) val otherTags: List<String>?)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val mapper = newGenerated("\$TestEntity_JdbcRowMapper").invoke() as JdbcRowMapper<*>
        val rs = mock<ResultSet>()
        whenever(rs.findColumn("id")).thenReturn(1)
        whenever(rs.findColumn("tags")).thenReturn(2)
        whenever(rs.findColumn("other_tags")).thenReturn(3)
        whenever(rs.getLong(1)).thenReturn(1L)
        whenever(rs.wasNull()).thenReturn(false, true)

        val result = mapper.apply(rs)

        assertThat(result).hasToString("TestEntity(id=1, tags=[], otherTags=[])")
    }

    @Test
    fun testOneToManyListResultSetMapperRejectsPartiallyNullChild() {
        compile0(
            listOf(JdbcEntitySymbolProcessorProvider()),
            """
            @EntityJdbc
            data class UserOrdersView(@field:Embedded("u_") val user: User, @field:Embedded("o_") val orders: List<Order>)

            @Table("users")
            data class User(@field:Id val id: String, val name: String)

            @Table("orders")
            data class Order(@field:Id val id: Long, @field:Column("user_id") val userId: String, val number: String)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val mapper = newGenerated("\$UserOrdersView_ListJdbcResultSetMapper").invoke() as JdbcResultSetMapper<*>
        val rs = mock<ResultSet>()
        whenever(rs.next()).thenReturn(true, false)
        whenever(rs.findColumn("u_id")).thenReturn(1)
        whenever(rs.findColumn("u_name")).thenReturn(2)
        whenever(rs.findColumn("o_id")).thenReturn(3)
        whenever(rs.findColumn("o_user_id")).thenReturn(4)
        whenever(rs.findColumn("o_number")).thenReturn(5)
        whenever(rs.getString(1)).thenReturn("u1")
        whenever(rs.getString(2)).thenReturn("User 1")
        whenever(rs.getLong(3)).thenReturn(1L)
        whenever(rs.getString(5)).thenReturn("n1")
        whenever(rs.wasNull()).thenReturn(false, false, false, true, false)

        assertThatThrownBy { mapper.apply(rs) }
            .isInstanceOf(NullPointerException::class.java)
            .hasMessage("Field userId is not nullable, but column o_user_id is null")
    }

    @Test
    fun testOneToManyListResultSetMapperHandlesEmptySingleFieldChild() {
        compile0(
            listOf(JdbcEntitySymbolProcessorProvider()),
            """
            @EntityJdbc
            data class ParentChildren(@field:Id val id: String, @field:Embedded("c_") val children: List<Child>)

            @Table("children")
            data class Child(@field:Id val id: Long)
            """.trimIndent()
        )
        compileResult.assertSuccess()

        val mapper = newGenerated("\$ParentChildren_ListJdbcResultSetMapper").invoke() as JdbcResultSetMapper<*>
        val rs = mock<ResultSet>()
        whenever(rs.next()).thenReturn(true, false)
        whenever(rs.findColumn("id")).thenReturn(1)
        whenever(rs.findColumn("c_id")).thenReturn(2)
        whenever(rs.getString(1)).thenReturn("p1")
        whenever(rs.getLong(2)).thenReturn(0L)
        whenever(rs.wasNull()).thenReturn(false, true)

        val result = mapper.apply(rs) as List<*>

        assertThat(result).hasSize(1)
        val children = result[0]!!.javaClass.getMethod("getChildren").invoke(result[0]) as List<*>
        assertThat(children).isEmpty()
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

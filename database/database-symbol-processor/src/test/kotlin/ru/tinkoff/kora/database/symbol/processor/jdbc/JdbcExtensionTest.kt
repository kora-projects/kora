package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultColumnMapper
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcRowMapper
import ru.tinkoff.kora.database.symbol.processor.entity.EntityWithEmbedded
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.ksp.common.TestUtils
import java.sql.ResultSet
import kotlin.reflect.typeOf

class JdbcExtensionTest {

    @Test
    fun testEntity() {
        TestUtils.testKoraExtension(
            arrayOf(
                typeOf<JdbcRowMapper<TestEntity>>(),
                typeOf<JdbcRowMapper<AllNativeTypesEntity>>(),
                typeOf<JdbcResultSetMapper<AllNativeTypesEntity>>(),
                typeOf<JdbcResultSetMapper<TestEntity>>(),
                typeOf<JdbcResultSetMapper<List<AllNativeTypesEntity>>>(),
                typeOf<JdbcResultSetMapper<List<TestEntity>>>(),
                typeOf<JdbcResultSetMapper<EntityWithEmbedded>>(),
                typeOf<JdbcResultSetMapper<List<EntityWithEmbedded>>>(),
            ),
            typeOf<JdbcResultColumnMapper<TestEntity.UnknownField>>(),
            typeOf<TestEntityFieldJdbcResultColumnMapperNonFinal>(),
        )
    }

    data class TestRow(val f1: String, val f2: String)

    @Test
    fun testRowMapper() {
        val cl = TestUtils.testKoraExtension(
            arrayOf(
                typeOf<JdbcResultSetMapper<TestRow>>(),
            )
        )!!
        val k = cl.loadClass("ru.tinkoff.kora.database.symbol.processor.jdbc.\$JdbcExtensionTest_TestRow_JdbcRowMapper")
        val mapper = k.constructors[0].newInstance() as JdbcRowMapper<TestRow>
        val rs = mock<ResultSet>()

        whenever(rs.findColumn("f1")).thenReturn(1)
        whenever(rs.findColumn("f2")).thenReturn(2)
        whenever(rs.getString(1)).thenReturn("test1")
        whenever(rs.getString(2)).thenReturn("test2")

        val o1 = mapper.apply(rs)

        assertThat(o1).isEqualTo(TestRow("test1", "test2"))
        verify(rs).getString(1)
        verify(rs).getString(2)
    }

    @Test
    fun testListResultSetMapper() {
        val cl = TestUtils.testKoraExtension(
            arrayOf(
                typeOf<JdbcResultSetMapper<List<TestRow>>>(),
            )
        )!!
        val k = cl.loadClass("ru.tinkoff.kora.database.symbol.processor.jdbc.\$JdbcExtensionTest_TestRow_JdbcListResultSetMapper")
        val mapper = k.constructors[0].newInstance() as JdbcResultSetMapper<List<TestRow>>
        val rs = mock<ResultSet>()

        whenever(rs.next()).thenReturn(true, true, false)
        whenever(rs.findColumn("f1")).thenReturn(1)
        whenever(rs.findColumn("f2")).thenReturn(2)
        whenever(rs.getString(1)).thenReturn("test1")
        whenever(rs.getString(2)).thenReturn("test2")
        val o1 = mapper.apply(rs)
        assertThat(o1).isEqualTo(listOf(TestRow("test1", "test2"), TestRow("test1", "test2")))
        verify(rs, times(3)).next()
        verify(rs, times(2)).getString(1)
        verify(rs, times(2)).getString(2)
        reset(rs)

        whenever(rs.next()).thenReturn(false)
        val o2 = mapper.apply(rs)
        assertThat(o2).isEmpty()
        verify(rs).next()
        verifyNoMoreInteractions(rs)
    }
}

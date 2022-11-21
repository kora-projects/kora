package ru.tinkoff.kora.database.symbol.processor.cassandra

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.annotation.processor.common.TestContext
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.database.cassandra.CassandraConnectionFactory
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraReactiveResultSetMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraResultSetMapper
import ru.tinkoff.kora.database.symbol.processor.DbTestUtils
import ru.tinkoff.kora.database.symbol.processor.cassandra.repository.AllowedResultsRepository

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CassandraResultsTest {
    private val testCtx = TestContext()
    private val executor = MockCassandraExecutor()
    private val repository: AllowedResultsRepository

    constructor() {
        testCtx.addContextElement(TypeRef.of(CassandraConnectionFactory::class.java), executor)
        testCtx.addMock(TypeRef.of(TestEntityCassandraRowMapperNonFinal::class.java))
        testCtx.addMock(TypeRef.of(CassandraResultSetMapper::class.java, java.lang.Integer::class.java))
        testCtx.addMock(TypeRef.of(CassandraResultSetMapper::class.java, java.lang.Integer::class.java))

        repository = testCtx.newInstance(DbTestUtils.compileClass(AllowedResultsRepository::class).java)
    }


    @BeforeEach
    internal fun setUp() {
        executor.reset()
    }

    @Test
    fun testReturnUnit() {
        repository.returnVoid()
    }
    /*
        @Test
        fun testReturnNativeType() {
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(true)
            Mockito.`when`(executor.iterator.next().getInt(0)).thenReturn(42)
            assertThat(repository.returnNativeType()).isEqualTo(42)
        }

        @Test
        fun testReturnNativeTypeNullable() {
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(true)
            Mockito.`when`(executor.iterator.next().getInt(0)).thenReturn(42)
            Mockito.`when`(executor.iterator.next().isNull(0)).thenReturn(false)
            assertThat(repository.returnNativeTypeNullable()).isEqualTo(42)
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(true)
            Mockito.`when`(executor.iterator.next().getInt(0)).thenReturn(-1)
            Mockito.`when`(executor.iterator.next().isNull(0)).thenReturn(true)
            assertThat(repository.returnNativeTypeNullable()).isNull()
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(false)
            assertThat(repository.returnNativeTypeNullable()).isNull()
        }

        @Test
        fun testReturnEntity() {
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(true)
            Mockito.`when`<String>(executor.iterator.next().getString("param1")).thenReturn("test1")
            Mockito.`when`(executor.iterator.next().getInt("param2")).thenReturn(42)
            Mockito.`when`<String>(executor.iterator.next().getString("param3")).thenReturn(null)
            Mockito.`when`(executor.iterator.next().isNull(0)).thenReturn(false, false, true)
            assertThat(repository.returnEntity()).isEqualTo(Entity("test1", 42, null))
        }

        @Test
        fun testReturnMutableEntity() {
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(true)
            Mockito.`when`<String>(executor.iterator.next().getString("param1")).thenReturn("test1")
            Mockito.`when`(executor.iterator.next().getInt("param2")).thenReturn(42)
            Mockito.`when`<String>(executor.iterator.next().getString("param3")).thenReturn(null)
            Mockito.`when`(executor.iterator.next().isNull(0)).thenReturn(false, false, true)
            assertThat(repository.returnMutableEntity())
                .hasFieldOrPropertyWithValue("param1", "test1")
                .hasFieldOrPropertyWithValue("param2", 42)
                .hasFieldOrPropertyWithValue("param3", null)
        }

        @Test
        fun testReturnNullableEntity() {
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(false)
            assertThat(repository.returnNullableEntity()).isNull()
        }

        @Test
        fun testReturnEntityRowMapper() {
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(true)
            Mockito.`when`<String>(executor.iterator.next().getString(0)).thenReturn("test1")
            Mockito.`when`(executor.iterator.next().isNull(0)).thenReturn(false, false, true)
            assertThat(repository.returnEntityRowMapper()).isEqualTo(MappedEntity("test1"))
        }

        @Test
        fun testReturnEntityResultSetMapper() {
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(true)
            Mockito.`when`<String>(executor.iterator.next().getString(0)).thenReturn("test1")
            Mockito.`when`(executor.iterator.next().isNull(0)).thenReturn(false, false, true)
            assertThat(repository.returnEntityResultSetMapper()).isEqualTo(MappedEntity("test1"))
        }

        @Test
        fun testReturnNativeTypeList() {
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(true, true, true, false)
            Mockito.`when`(executor.iterator.next().getInt(0)).thenReturn(42, 43, 44)
            Mockito.`when`(executor.iterator.next().isNull(0)).thenReturn(false, false, false)
            assertThat(repository.returnNativeTypeList()).containsExactly(42, 43, 44)
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(false)
            assertThat(repository.returnNativeTypeList()).isEmpty()
        }

        @Test
        fun testReturnListEntity() {
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(true, true, true, false)
            Mockito.`when`<String>(executor.iterator.next().getString("param1")).thenReturn("test1", "test2", "test3")
            Mockito.`when`(executor.iterator.next().getInt("param2")).thenReturn(42, 43, 44)
            Mockito.`when`<String>(executor.iterator.next().getString("param3")).thenReturn(null)
            Mockito.`when`(executor.iterator.next().isNull(0))
                .thenReturn(false, false, true, false, false, true, false, false, true)
            assertThat(repository.returnListEntity()).containsExactly(
                Entity("test1", 42, null),
                Entity("test2", 43, null),
                Entity("test3", 44, null)
            )
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(false)
            assertThat(repository.returnListEntity()).isEmpty()
        }

        @Test
        fun testReturnListWithRowMapper() {
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(true, true, false)
            Mockito.`when`<String>(executor.iterator.next().getString(0)).thenReturn("val1", "val2")
            Mockito.`when`(executor.iterator.next().isNull(0)).thenReturn(false, false)
            assertThat(repository.returnListWithRowMapper()).containsExactly(MappedEntity("val1"), MappedEntity("val2"))
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(false)
            assertThat(repository.returnListWithRowMapper()).isEmpty()
        }

        @Test
        fun testReturnListWithResultSetMapper() {
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(true, true, false)
            Mockito.`when`<String>(executor.iterator.next().getString(0)).thenReturn("val1", "val2")
            Mockito.`when`(executor.iterator.next().isNull(0)).thenReturn(false, false)
            assertThat(repository.returnListWithResultSetMapper()).containsExactly(
                MappedEntity("val1"),
                MappedEntity("val2")
            )
            Mockito.`when`(executor.iterator.hasNext()).thenReturn(false)
            assertThat(repository.returnListWithResultSetMapper()).isEmpty()
        }

     */
}

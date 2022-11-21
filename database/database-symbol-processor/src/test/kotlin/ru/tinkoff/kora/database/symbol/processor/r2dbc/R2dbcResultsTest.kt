package ru.tinkoff.kora.database.symbol.processor.r2dbc

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.annotation.processor.common.TestContext
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.database.r2dbc.R2dbcConnectionFactory
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultColumnMapper
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultFluxMapper
import ru.tinkoff.kora.database.symbol.processor.DbTestUtils
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.database.symbol.processor.r2dbc.repository.AllowedResultsRepository
import ru.tinkoff.kora.database.symbol.processor.r2dbc.repository.AllowedSuspendResultsRepository

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcResultsTest {
    private val executor = MockR2dbcExecutor()
    private val repository: AllowedResultsRepository
    private val suspendRepository: AllowedSuspendResultsRepository
    val ctx = TestContext()

    init {
        ctx.addContextElement(TypeRef.of(R2dbcConnectionFactory::class.java), executor)
        ctx.addMock(TypeRef.of(TestEntityFieldR2dbcResultColumnMapperNonFinal::class.java))
        ctx.addMock(TypeRef.of(TestEntityR2dbcRowMapperNonFinal::class.java))
        ctx.addMock(TypeRef.of(R2dbcResultColumnMapper::class.java, TestEntity.UnknownField::class.java))
        ctx.addMock(TypeRef.of(R2dbcResultFluxMapper::class.java, Int::class.javaObjectType, TypeRef.of(Mono::class.java, Int::class.javaObjectType)))
        ctx.addMock(TypeRef.of(R2dbcResultFluxMapper::class.java, Int::class.javaObjectType, TypeRef.of(Flux::class.java, Int::class.javaObjectType)))
        repository = ctx.newInstance(DbTestUtils.compileClass(AllowedResultsRepository::class).java)
        suspendRepository = ctx.newInstance(DbTestUtils.compileClass(AllowedSuspendResultsRepository::class).java)
    }

    @BeforeEach
    internal fun setUp() {
        executor.reset()
    }


    @Test
    fun testReturnVoid() {
        repository.returnVoid()
    }
//
//    @Test
//    fun testReturnNativeType() {
//        executor.setRow(MockColumn("test", 42))
//        assertThat(repository.returnNativeType().block()).isEqualTo(42)
//        executor.setRow(MockColumn("test", null))
//        Assertions.assertThatThrownBy { repository.returnNativeType().block() }
//        executor.setRows(listOf())
//        assertThat(repository.returnNativeType().block()).isNull()
//    }
//
//    @Test
//    fun testReturnEntity() {
//        executor.setRow(
//            listOf(
//                MockColumn("param1", "test1"),
//                MockColumn("param2", 42),
//                MockColumn("param3", null)
//            )
//        )
//        assertThat(repository.returnEntity().block()).isEqualTo(Entity("test1", 42, null))
//        executor.setRows(listOf())
//        assertThat(repository.returnEntity().block()).isNull()
//        executor.setRow(
//            listOf(
//                MockColumn("param1", null),
//                MockColumn("param2", 42),
//                MockColumn("param3", null)
//            )
//        )
//        Assertions.assertThatThrownBy { repository.returnEntity().block() }.isInstanceOf(NullPointerException::class.java)
//    }
//
//    @Test
//    fun testReturnMutableEntity() {
//        executor.setRow(
//            listOf(
//                MockColumn("param1", "test1"),
//                MockColumn("param2", 42),
//                MockColumn("param3", null)
//            )
//        )
//        assertThat(repository.returnMutableEntity().block())
//            .hasFieldOrPropertyWithValue("param1", "test1")
//            .hasFieldOrPropertyWithValue("param2", 42)
//            .hasFieldOrPropertyWithValue("param3", null)
//        executor.setRows(listOf())
//        assertThat(repository.returnMutableEntity().block()).isNull()
//        executor.setRow(
//            listOf(
//                MockColumn("param1", null),
//                MockColumn("param2", 42),
//                MockColumn("param3", null)
//            )
//        )
//        Assertions.assertThatThrownBy { repository.returnMutableEntity().block() }.isInstanceOf(NullPointerException::class.java)
//    }
//
//    @Test
//    fun testReturnEntityWithMappedRow() {
//        executor.setRow(
//            listOf(
//                MockColumn("param1", "test1"),
//                MockColumn("param2", "test2")
//            )
//        )
//        assertThat(
//            repository.returnEntityWithMappedRow().block()
//        ).isEqualTo(
//            EntityWithMappedColumn(
//                "test1",
//                MappedEntityColumn("test2")
//            )
//        )
//    }
//
//    @Test
//    fun testReturnEntityRowMapper() {
//        executor.setRow(
//            MockColumn("param1", "test1")
//        )
//        assertThat(repository.returnEntityRowMapper().block()).isEqualTo(MappedEntity("test1"))
//    }
//
//    @Test
//    fun testReturnColumnMapper() {
//        executor.setRow(
//            MockColumn("test_column", "test1")
//        )
//        assertThat(repository.returnColumnMapper().block()).isEqualTo(MappedEntityColumn("test1"))
//    }
//
//    @Test
//    fun testReturnEntityRowSetMapper() {
//        executor.setRow(
//            MockColumn("test_column", "test1")
//        )
//        assertThat(repository.returnEntityRowSetMapper().block()).isEqualTo(MappedEntity("test1"))
//    }
//
//    @Test
//    fun testReturnNativeTypeList() {
//        executor.setRows(
//            listOf(
//                listOf(MockColumn("test_column", 42)),
//                listOf(MockColumn("test_column", 43)),
//                listOf(MockColumn("test_column", 44))
//            )
//        )
//        assertThat(repository.returnNativeTypeList().block()).containsExactly(42, 43, 44)
//        executor.setRows(listOf())
//        assertThat(repository.returnNativeTypeList().block()).isEmpty()
//    }
//
//
//    @Test
//    fun testReturnListEntity() {
//        executor.setRows(
//            listOf(
//                listOf(
//                    MockColumn("param1", "test1"),
//                    MockColumn("param2", 42),
//                    MockColumn("param3", null)
//                ),
//                listOf(
//                    MockColumn("param1", "test2"),
//                    MockColumn("param2", 43),
//                    MockColumn("param3", null)
//                ),
//                listOf(
//                    MockColumn("param1", "test3"),
//                    MockColumn("param2", 44),
//                    MockColumn("param3", null)
//                )
//            )
//        )
//        assertThat(repository.returnListEntity().block()).containsExactly(
//            Entity("test1", 42, null),
//            Entity("test2", 43, null),
//            Entity("test3", 44, null)
//        )
//        executor.setRows(listOf())
//        assertThat(repository.returnListEntity().block()).isEmpty()
//    }
//
//    @Test
//    fun testReturnListWithRowMapper() {
//        executor.setRows(
//            listOf(
//                listOf(MockColumn("param1", "val1")),
//                listOf(MockColumn("param1", "val2"))
//            )
//        )
//        assertThat(repository.returnListWithRowMapper().block()).containsExactly(
//            MappedEntity("val1"),
//            MappedEntity("val2")
//        )
//    }
//
//    @Test
//    fun testReturnListWithRowSetMapper() {
//        executor.setRows(
//            listOf(
//                listOf(MockColumn("param1", "val1")),
//                listOf(MockColumn("param1", "val2"))
//            )
//        )
//        assertThat(repository.returnListWithRowSetMapper().block()).containsExactly(
//            MappedEntity("val1"),
//            MappedEntity("val2")
//        )
//    }
}

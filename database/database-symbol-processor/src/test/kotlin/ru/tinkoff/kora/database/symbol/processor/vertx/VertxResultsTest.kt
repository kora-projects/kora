package ru.tinkoff.kora.database.symbol.processor.vertx

import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.annotation.processor.common.TestContext
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.database.symbol.processor.DbTestUtils
import ru.tinkoff.kora.database.symbol.processor.vertx.repository.AllowedResultsRepository
import ru.tinkoff.kora.database.symbol.processor.vertx.repository.AllowedSuspendResultsRepository
import ru.tinkoff.kora.database.vertx.VertxConnectionFactory
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowMapper
import ru.tinkoff.kora.database.vertx.mapper.result.VertxRowSetMapper

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VertxResultsTest {
    private val executor = MockVertxExecutor()
    private val ctx = TestContext()
    private val repository: AllowedResultsRepository
    private val repositorySuspend: AllowedSuspendResultsRepository

    init {
        ctx.addMock(TypeRef.of(VertxRowMapper::class.java, Int::class.javaObjectType))
        ctx.addMock(TypeRef.of(VertxRowSetMapper::class.java, Int::class.javaObjectType))
        ctx.addMock(TypeRef.of(TestEntityVertxRowMapperNonFinal::class.java))
        ctx.addContextElement(TypeRef.of(VertxConnectionFactory::class.java), executor)
        repository = ctx.newInstance(DbTestUtils.compileClass(AllowedResultsRepository::class).java)
        repositorySuspend = ctx.newInstance(DbTestUtils.compileClass(AllowedSuspendResultsRepository::class).java)
    }

    @BeforeEach
    fun setUp() {
        executor.reset()
    }

    //
    @Test
    fun testReturnObject() {
        whenever(ctx.findInstance(TypeRef.of(VertxRowSetMapper::class.java, Int::class.javaObjectType)).apply(any())).thenReturn(10)
        repository.returnObject()
    }
//
//    @Test
//    fun testReturnNativeType() {
//        executor.setRow(MockVertxExecutor.MockColumn("test", 42))
//        assertThat(repository.returnNativeType().block()).isEqualTo(42)
//        executor.setRow(MockVertxExecutor.MockColumn("test", null))
//        assertThat(repository.returnNativeType().block()).isNull()
//        executor.setRows(listOf())
//        assertThat(repository.returnNativeType().block()).isNull()
//    }
//
//    @Test
//    fun testReturnEntity() {
//        executor.setRow(
//            listOf(
//                MockVertxExecutor.MockColumn("param1", "test1"),
//                MockVertxExecutor.MockColumn("param2", 42),
//                MockVertxExecutor.MockColumn("param3", null)
//            )
//        )
//        assertThat(repository.returnEntity().block()).isEqualTo(AllowedResultsRepository.Entity("test1", 42, null))
//        executor.setRows(listOf())
//        assertThat(repository.returnEntity().block()).isNull()
//        executor.setRow(
//            listOf(
//                MockVertxExecutor.MockColumn("param1", null),
//                MockVertxExecutor.MockColumn("param2", 42),
//                MockVertxExecutor.MockColumn("param3", null)
//            )
//        )
//        Assertions.assertThatThrownBy { repository.returnEntity().block() }.isInstanceOf(NullPointerException::class.java)
//    }
//
//    @Test
//    fun testReturnMutableEntity() {
//        executor.setRow(
//            listOf(
//                MockVertxExecutor.MockColumn("param1", "test1"),
//                MockVertxExecutor.MockColumn("param2", 42),
//                MockVertxExecutor.MockColumn("param3", null)
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
//                MockVertxExecutor.MockColumn("param1", null),
//                MockVertxExecutor.MockColumn("param2", 42),
//                MockVertxExecutor.MockColumn("param3", null)
//            )
//        )
//        Assertions.assertThatThrownBy { repository.returnMutableEntity().block() }.isInstanceOf(NullPointerException::class.java)
//    }
//
//    @Test
//    fun testReturnEntityWithMappedRow() {
//        executor.setRow(
//            listOf(
//                MockVertxExecutor.MockColumn("param1", "test1"),
//                MockVertxExecutor.MockColumn("param2", "test2")
//            )
//        )
//        assertThat(
//            repository.returnEntityWithMappedRow().block()
//        ).isEqualTo(
//            AllowedResultsRepository.EntityWithMappedColumn(
//                "test1",
//                AllowedResultsRepository.MappedEntityColumn("test2")
//            )
//        )
//    }
//
//    @Test
//    fun testReturnEntityRowMapper() {
//        executor.setRow(
//            MockVertxExecutor.MockColumn("param1", "test1")
//        )
//        assertThat(repository.returnEntityRowMapper().block()).isEqualTo(AllowedResultsRepository.MappedEntity("test1"))
//    }
//
//    @Test
//    fun testReturnColumnMapper() {
//        executor.setRow(
//            MockVertxExecutor.MockColumn("test_column", "test1")
//        )
//        assertThat(repository.returnColumnMapper().block()).isEqualTo(AllowedResultsRepository.MappedEntityColumn("test1"))
//    }
//
//    @Test
//    fun testReturnEntityRowSetMapper() {
//        executor.setRow(
//            MockVertxExecutor.MockColumn("test_column", "test1")
//        )
//        assertThat(repository.returnEntityRowSetMapper().block()).isEqualTo(AllowedResultsRepository.MappedEntity("test1"))
//    }
//
//
//    @Test
//    fun testReturnNativeTypeList() {
//        executor.setRows(
//            listOf(
//                listOf(MockVertxExecutor.MockColumn("test_column", 42)),
//                listOf(MockVertxExecutor.MockColumn("test_column", 43)),
//                listOf(MockVertxExecutor.MockColumn("test_column", 44))
//            )
//        )
//        assertThat(repository.returnNativeTypeList().block()).containsExactly(42, 43, 44)
//        executor.setRows(listOf())
//        assertThat(repository.returnNativeTypeList().block()).isEmpty()
//    }
//
//    @Test
//    fun testReturnListEntity() {
//        executor.setRows(
//            listOf(
//                listOf(
//                    MockVertxExecutor.MockColumn("param1", "test1"),
//                    MockVertxExecutor.MockColumn("param2", 42),
//                    MockVertxExecutor.MockColumn("param3", null)
//                ),
//                listOf(
//                    MockVertxExecutor.MockColumn("param1", "test2"),
//                    MockVertxExecutor.MockColumn("param2", 43),
//                    MockVertxExecutor.MockColumn("param3", null)
//                ),
//                listOf(
//                    MockVertxExecutor.MockColumn("param1", "test3"),
//                    MockVertxExecutor.MockColumn("param2", 44),
//                    MockVertxExecutor.MockColumn("param3", null)
//                )
//            )
//        )
//        assertThat(repository.returnListEntity().block()).containsExactly(
//            AllowedResultsRepository.Entity("test1", 42, null),
//            AllowedResultsRepository.Entity("test2", 43, null),
//            AllowedResultsRepository.Entity("test3", 44, null)
//        )
//        executor.setRows(listOf())
//        assertThat(repository.returnListEntity().block()).isEmpty()
//    }
//
//    @Test
//    fun testReturnListWithRowMapper() {
//        executor.setRows(
//            listOf(
//                listOf(MockVertxExecutor.MockColumn("param1", "val1")),
//                listOf(MockVertxExecutor.MockColumn("param1", "val2"))
//            )
//        )
//        assertThat(repository.returnListWithRowMapper().block()).containsExactly(
//            AllowedResultsRepository.MappedEntity("val1"),
//            AllowedResultsRepository.MappedEntity("val2")
//        )
//    }
//
//    @Test
//    fun testReturnListWithRowSetMapper() {
//        executor.setRows(
//            listOf(
//                listOf(MockVertxExecutor.MockColumn("param1", "val1")),
//                listOf(MockVertxExecutor.MockColumn("param1", "val2"))
//            )
//        )
//        assertThat(repository.returnListWithRowSetMapper().block()).containsExactly(
//            AllowedResultsRepository.MappedEntity("val1"),
//            AllowedResultsRepository.MappedEntity("val2")
//        )
//    }
}

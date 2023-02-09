package ru.tinkoff.kora.database.symbol.processor.cassandra

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import reactor.core.publisher.Flux
import ru.tinkoff.kora.annotation.processor.common.TestContext
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.database.cassandra.CassandraConnectionFactory
import ru.tinkoff.kora.database.cassandra.mapper.parameter.CassandraParameterColumnMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraReactiveResultSetMapper
import ru.tinkoff.kora.database.symbol.processor.DbTestUtils
import ru.tinkoff.kora.database.symbol.processor.cassandra.repository.AllowedParametersRepository
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CassandraParametersTest {
    private val testCtx = TestContext()
    private val executor = MockCassandraExecutor()
    private val repository: AllowedParametersRepository

    init {
        testCtx.addContextElement(TypeRef.of(CassandraConnectionFactory::class.java), executor)
        testCtx.addMock(TypeRef.of(CassandraParameterColumnMapper::class.java, TestEntity.UnknownField::class.java))
        testCtx.addMock(TypeRef.of(TestEntityFieldCassandraParameterColumnMapperNonFinal::class.java))
        testCtx.addMock(TypeRef.of(CassandraReactiveResultSetMapper::class.java, java.lang.Integer::class.java, TypeRef.of(Flux::class.java, java.lang.Integer::class.java)))
        repository = testCtx.newInstance(DbTestUtils.compileClass(AllowedParametersRepository::class).java)
    }

    @BeforeEach
    internal fun setUp() {
        executor.reset()
    }

    @Test
    fun testNativeParameter() {
        repository.nativeParameter("test", 42)
        Mockito.verify(executor.boundStatementBuilder).setString(0, "test")
        Mockito.verify(executor.boundStatementBuilder).setInt(1, 42)
    }

    @Test
    fun testDataClassParameter() {
        repository.dataClassParameter(TestEntity(
            "field1",
            2,
            null,
            TestEntity.UnknownField(),
            TestEntity.MappedField1(),
            TestEntity.MappedField2()
        ))
        Mockito.verify(executor.boundStatementBuilder).setString(0, "field1")
        Mockito.verify(executor.boundStatementBuilder).setInt(1, 2)
        Mockito.verify(executor.boundStatementBuilder).setToNull(2)
    }

    @Test
    fun parametersWithSimilarNames() {
        repository.parametersWithSimilarNames("test", 42)
        Mockito.verify(executor.mockSession).prepare("INSERT INTO test(value1, value2) VALUES (?, ?)")
        Mockito.verify(executor.boundStatementBuilder).setString(0, "test")
        Mockito.verify(executor.boundStatementBuilder).setInt(1, 42)
    }
}

package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.mockito.kotlin.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import ru.tinkoff.kora.annotation.processor.common.TestContext
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory
import ru.tinkoff.kora.database.jdbc.mapper.parameter.JdbcParameterColumnMapper
import ru.tinkoff.kora.database.symbol.processor.DbTestUtils
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.database.symbol.processor.jdbc.repository.AllowedParametersRepository
import java.sql.Types
import java.util.concurrent.Executor

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcParametersTest {
    private val executor = MockJdbcExecutor()
    private val repository: AllowedParametersRepository
    private val ctx = TestContext()

    init {
        ctx.addContextElement(TypeRef.of(JdbcConnectionFactory::class.java), executor)
        ctx.addMock(TypeRef.of(JdbcParameterColumnMapper::class.java, TestEntity.UnknownField::class.java))
        ctx.addMock(TypeRef.of(TestEntityFieldJdbcParameterColumnMapperNonFinal::class.java))
        ctx.addMock(TypeRef.of(Executor::class.java))
        repository = ctx.newInstance(DbTestUtils.compileClass(AllowedParametersRepository::class).java)
    }


    @BeforeEach
    internal fun setUp() {
        executor.reset()
    }

    @Test
    fun testNativeParameter() {
        repository.nativeParameter(null, 10)
        verify(executor.preparedStatement).setNull(1, Types.VARCHAR)
        verify(executor.preparedStatement).setInt(2, 10)
        verify(executor.preparedStatement).execute()
        verify(executor.preparedStatement).updateCount
    }


    @Test
    fun parametersWithSimilarNames() {
        repository.parametersWithSimilarNames("test", 42)
        Mockito.verify(executor.mockConnection).prepareStatement("INSERT INTO test(value1, value2) VALUES (?, ?)")
        Mockito.verify(executor.preparedStatement).setString(1, "test")
        Mockito.verify(executor.preparedStatement).setInt(2, 42)
    }
}

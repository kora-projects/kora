package ru.tinkoff.kora.database.symbol.processor.r2dbc

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito
import reactor.core.publisher.Flux
import ru.tinkoff.kora.annotation.processor.common.TestContext
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.database.r2dbc.R2dbcConnectionFactory
import ru.tinkoff.kora.database.r2dbc.mapper.parameter.R2dbcParameterColumnMapper
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultFluxMapper
import ru.tinkoff.kora.database.symbol.processor.DbTestUtils
import ru.tinkoff.kora.database.symbol.processor.entity.TestEntity
import ru.tinkoff.kora.database.symbol.processor.r2dbc.repository.AllowedParametersRepository

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class R2dbcParametersTest {
    private val executor = MockR2dbcExecutor()
    private val repository: AllowedParametersRepository
    val ctx = TestContext()

    init {
        ctx.addContextElement(TypeRef.of(R2dbcConnectionFactory::class.java), executor)
        ctx.addMock(TypeRef.of(TestEntityFieldR2dbcParameterColumnMapperNonFinal::class.java))
        ctx.addMock(TypeRef.of(R2dbcParameterColumnMapper::class.java, TestEntity.UnknownField::class.java))
        ctx.addMock(TypeRef.of(R2dbcResultFluxMapper::class.java, Int::class.javaObjectType, TypeRef.of(Flux::class.java, Int::class.javaObjectType)))
        repository = ctx.newInstance(DbTestUtils.compileClass(AllowedParametersRepository::class).java)
    }


    @BeforeEach
    internal fun setUp() {
        executor.reset()
    }

    @Test
    fun testNativeParameter() {
        repository.nativeParameter("test", 42)
        Mockito.verify(executor.statement).bind(0, "test")
        Mockito.verify(executor.statement).bind(1, 42)
        Mockito.verify(executor.statement).execute()
    }


    @Test
    fun testParametersWithSimilarNames() {
        runBlocking {
            repository.parametersWithSimilarNames("test", 42)
        }
        Mockito.verify(executor.con).createStatement("INSERT INTO test(value1, value2) VALUES ($1, $2)")
        Mockito.verify(executor.statement).bind(0, "test")
        Mockito.verify(executor.statement).bind(1, 42)
    }

}

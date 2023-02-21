package ru.tinkoff.kora.database.symbol.processor.jdbc

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.annotation.processor.common.TestContext
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultSetMapper
import ru.tinkoff.kora.database.symbol.processor.DbTestUtils
import ru.tinkoff.kora.database.symbol.processor.jdbc.repository.AllowedSuspendResultsRepository
import java.util.*
import java.util.concurrent.Executor

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSuspendResultsTest {
    private val executor: MockJdbcExecutor = MockJdbcExecutor()
    private val repository: AllowedSuspendResultsRepository
    private val ctx = TestContext()

    init {
        ctx.addContextElement(
            TypeRef.of(
                JdbcConnectionFactory::class.java
            ), executor
        )
        ctx.addContextElement(TypeRef.of(Executor::class.java),
            Executor { obj: Runnable -> obj.run() })
        ctx.addMock(TypeRef.of(TestEntityJdbcRowMapperNonFinal::class.java))
        ctx.addMock(TypeRef.of(TestEntityFieldJdbcResultColumnMapperNonFinal::class.java))
        ctx.addMock(TypeRef.of(JdbcResultSetMapper::class.java, Void::class.java))
        ctx.addMock(TypeRef.of(JdbcResultSetMapper::class.java, Int::class.javaObjectType))
        ctx.addMock(TypeRef.of(JdbcResultSetMapper::class.java, TypeRef.of(List::class.java, Int::class.javaObjectType)))
        ctx.addMock(TypeRef.of(JdbcResultSetMapper::class.java, TypeRef.of(Optional::class.java, Int::class.javaObjectType)))
        repository = ctx.newInstance(DbTestUtils.compileClass(AllowedSuspendResultsRepository::class).java)
    }

    @BeforeEach
    internal fun setUp() {
        executor.reset()
    }

    @Test
    fun testReturnVoid() {
        runBlocking {
            repository.returnVoid()
        }
    }
}

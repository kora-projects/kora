package ru.tinkoff.kora.database.symbol.processor.cassandra

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.annotation.processor.common.TestContext
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.database.cassandra.CassandraConnectionFactory
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraAsyncResultSetMapper
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraRowMapper
import ru.tinkoff.kora.database.symbol.processor.DbTestUtils
import ru.tinkoff.kora.database.symbol.processor.cassandra.repository.AllowedSuspendResultsRepository
import java.util.concurrent.CompletableFuture

class CassandraSuspendResultsTest() {
    private val testCtx = TestContext()
    private val executor = MockCassandraExecutor()
    private val repository: AllowedSuspendResultsRepository

    init {
        testCtx.addContextElement(TypeRef.of(CassandraConnectionFactory::class.java), executor)
        testCtx.addContextElement(TypeRef.of(CassandraAsyncResultSetMapper::class.java, Unit::class.java), CassandraAsyncResultSetMapper {
            CompletableFuture.completedFuture(Unit)
        })
        testCtx.addMock(TypeRef.of(CassandraAsyncResultSetMapper::class.java, java.lang.Integer::class.java))
        testCtx.addMock(TypeRef.of(CassandraRowMapper::class.java, java.lang.Integer::class.java))
        testCtx.addMock(TypeRef.of(TestEntityCassandraRowMapperNonFinal::class.java))
        repository = testCtx.newInstance(DbTestUtils.compileClass(AllowedSuspendResultsRepository::class).java)
    }


    @Test
    fun testReturnUnit() = runBlocking {
        repository.returnVoid()
    }

}

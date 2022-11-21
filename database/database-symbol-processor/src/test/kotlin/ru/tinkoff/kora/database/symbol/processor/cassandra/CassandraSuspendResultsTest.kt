package ru.tinkoff.kora.database.symbol.processor.cassandra

import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.annotation.processor.common.TestContext
import ru.tinkoff.kora.application.graph.TypeRef
import ru.tinkoff.kora.database.cassandra.CassandraConnectionFactory
import ru.tinkoff.kora.database.cassandra.mapper.result.CassandraReactiveResultSetMapper
import ru.tinkoff.kora.database.symbol.processor.DbTestUtils
import ru.tinkoff.kora.database.symbol.processor.cassandra.repository.AllowedSuspendResultsRepository

class CassandraSuspendResultsTest {
    private val testCtx = TestContext()
    private val executor = MockCassandraExecutor()
    private val repository: AllowedSuspendResultsRepository

    constructor() {
        testCtx.addContextElement(TypeRef.of(CassandraConnectionFactory::class.java), executor)
        testCtx.addContextElement(TypeRef.of(CassandraReactiveResultSetMapper::class.java, Unit::class.java, TypeRef.of(Mono::class.java, Unit::class.java)), CassandraReactiveResultSetMapper {
            mono {
                it.asFlow().lastOrNull()
            }
        })
        testCtx.addMock(TypeRef.of(CassandraReactiveResultSetMapper::class.java, java.lang.Integer::class.java, TypeRef.of(Mono::class.java, java.lang.Integer::class.java)))
        testCtx.addMock(TypeRef.of(CassandraReactiveResultSetMapper::class.java, java.lang.Integer::class.java, TypeRef.of(Flux::class.java, java.lang.Integer::class.java)))
        testCtx.addMock(TypeRef.of(TestEntityCassandraRowMapperNonFinal::class.java))
        repository = testCtx.newInstance(DbTestUtils.compileClass(AllowedSuspendResultsRepository::class).java)
    }


    @Test
    fun testReturnUnit() = runBlocking {
        repository.returnVoid()
    }

}

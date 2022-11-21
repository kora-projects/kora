package ru.tinkoff.kora.database.symbol.processor.cassandra

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet
import com.datastax.dse.driver.api.core.cql.reactive.ReactiveRow
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.*
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.database.cassandra.CassandraConnectionFactory
import ru.tinkoff.kora.database.common.QueryContext
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry
import java.util.concurrent.CompletableFuture
import java.util.function.Function

class MockCassandraExecutor : CassandraConnectionFactory {
    val resultSet: ResultSet = Mockito.mock(
        ResultSet::class.java
    )
    val asyncResultSet: ReactiveResultSet = Mockito.mock(ReactiveResultSet::class.java)
    val boundStatementBuilder: BoundStatementBuilder = Mockito.mock(BoundStatementBuilder::class.java)
    val preparedStatement: PreparedStatement = Mockito.mock(
        PreparedStatement::class.java
    )
    val boundStatement: BoundStatement = Mockito.mock(
        BoundStatement::class.java
    )
    val mockSession: CqlSession = Mockito.mock(CqlSession::class.java)
    val iterator: Iterator<Row> = Mockito.mock(
        MutableIterator::class.java
    ) as Iterator<Row>
    val row = Mockito.mock(Row::class.java)
    val batchStatementBuilder: BatchStatementBuilder = Mockito.mock(BatchStatementBuilder::class.java)
    val telemetry = Mockito.mock(DataBaseTelemetry::class.java)
    val telemetryCtx = Mockito.mock(DataBaseTelemetry.DataBaseTelemetryContext::class.java)

    fun reset() {
        Mockito.reset(resultSet, asyncResultSet, boundStatementBuilder, preparedStatement, boundStatement, mockSession, iterator, row, batchStatementBuilder, telemetry)
        Mockito.`when`(boundStatementBuilder.build()).thenReturn(boundStatement)
        Mockito.`when`<Iterator<Row>>(resultSet.iterator()).thenReturn(iterator)
        Mockito.`when`(resultSet.one()).thenReturn(row)
        Mockito.`when`(iterator.next()).thenReturn(row)
        Mockito.`when`(mockSession.prepare(ArgumentMatchers.anyString())).thenReturn(preparedStatement)
        Mockito.`when`(mockSession.prepareAsync(ArgumentMatchers.anyString())).thenReturn(CompletableFuture.completedFuture(preparedStatement))
        Mockito.`when`(preparedStatement.boundStatementBuilder()).thenReturn(boundStatementBuilder)
        Mockito.`when`(boundStatementBuilder.build()).thenReturn(boundStatement)
        Mockito.mockStatic(BatchStatement::class.java).use { ignored ->
            Mockito.`when`(BatchStatement.builder(DefaultBatchType.UNLOGGED)).thenReturn(batchStatementBuilder)
        }
        Mockito.`when`(telemetry.createContext(any(), any())).thenReturn(telemetryCtx)
        whenever(mockSession.executeReactive(any<Statement<*>>())).thenReturn(MockReactiveResultSet(listOf()))
        whenever(mockSession.execute(any<Statement<*>>())).thenReturn(resultSet)
    }

    init {
        reset()
    }

    override fun currentSession(): CqlSession {
        return mockSession
    }

    override fun telemetry(): DataBaseTelemetry {
        return telemetry
    }

    fun <T : Any?> query(query: QueryContext?, profile: String?, statementSetter: Function<BoundStatementBuilder, Statement<*>>, resultExtractor: Function<ResultSet, T>?): T {
        Mockito.clearInvocations(resultSet, boundStatementBuilder, batchStatementBuilder, boundStatement)
        val sb = mockSession.prepare(query!!.sql()).boundStatementBuilder()
        val statement: Statement<out Statement<*>?> = statementSetter.apply(sb)
        Mockito.`when`(mockSession.execute(statement)).thenReturn(resultSet)
        return resultExtractor!!.apply(resultSet)
    }

    class MockReactiveResultSet(private val rows: List<ReactiveRow>) : ReactiveResultSet {

        override fun subscribe(p0: Subscriber<in ReactiveRow>) {
            Flux.fromIterable(rows).subscribe(p0)
        }

        override fun getColumnDefinitions(): Publisher<out ColumnDefinitions> {
            TODO("Not yet implemented")
        }

        override fun getExecutionInfos(): Publisher<out ExecutionInfo> {
            TODO("Not yet implemented")
        }

        override fun wasApplied(): Publisher<Boolean> {
            return Mono.just(true)
        }

    }

}

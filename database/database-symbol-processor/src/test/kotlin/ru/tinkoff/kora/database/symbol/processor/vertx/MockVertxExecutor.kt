package ru.tinkoff.kora.database.symbol.processor.vertx

import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.sqlclient.PreparedQuery
import io.vertx.sqlclient.PreparedStatement
import io.vertx.sqlclient.Row
import io.vertx.sqlclient.RowSet
import io.vertx.sqlclient.SqlConnection
import org.mockito.Mockito
import reactor.core.publisher.Mono
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry.DataBaseTelemetryContext
import ru.tinkoff.kora.database.vertx.VertxConnectionFactory
import java.util.function.Function

class MockVertxExecutor : VertxConnectionFactory {
    val connection = Mockito.mock(SqlConnection::class.java)
    val statement = Mockito.mock(PreparedStatement::class.java)
    val query: PreparedQuery<RowSet<Row>> = Mockito.mock(PreparedQuery::class.java) as PreparedQuery<RowSet<Row>>
    val rowSet: RowSet<Row> = Mockito.mock(RowSet::class.java) as RowSet<Row>
    val telemetry = Mockito.mock(DataBaseTelemetry::class.java)
    val telemetryCtx = Mockito.mock(DataBaseTelemetryContext::class.java)

    init {
        reset()
    }

    fun reset() {
        whenever(telemetry.createContext(any(), any())).thenReturn(telemetryCtx)
        doAnswer {
            (it.arguments[1] as Handler<AsyncResult<PreparedStatement>>).handle(Future.succeededFuture(statement))
        }.`when`(connection).prepare(any(), any<Handler<AsyncResult<PreparedStatement>>>())
        whenever(statement.query()).thenReturn(query)
        whenever(query.execute(any(), any())).thenAnswer {
            (it.arguments[1] as Handler<AsyncResult<RowSet<Row>>>).handle(Future.succeededFuture(rowSet))
        }
    }

    override fun currentConnection() = Mono.just(connection)

    override fun newConnection() = Mono.just(connection)

    override fun telemetry() = telemetry

    override fun <T : Any?> withConnection(callback: Function<SqlConnection, Mono<T>>?): Mono<T> {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> inTx(callback: Function<SqlConnection, Mono<T>>?): Mono<T> {
        TODO("Not yet implemented")
    }


}

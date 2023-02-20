package ru.tinkoff.kora.database.symbol.processor.vertx

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.sqlclient.*
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry.DataBaseTelemetryContext
import ru.tinkoff.kora.database.vertx.VertxConnectionFactory
import java.util.concurrent.CompletableFuture
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
        org.mockito.kotlin.reset(connection, statement, query, rowSet, telemetry, telemetryCtx)
        whenever(telemetry.createContext(any(), any())).thenReturn(telemetryCtx)
        whenever(connection.preparedQuery(any())).thenReturn(query)
        whenever(statement.query()).thenReturn(query)
        whenever(query.execute(any(), any())).thenAnswer {
            (it.arguments[1] as Handler<AsyncResult<RowSet<Row>>>).handle(Future.succeededFuture(rowSet))
        }
    }

    override fun currentConnection() = connection

    override fun newConnection() = CompletableFuture.completedFuture(connection)
    override fun pool(): Pool {
        TODO("Not yet implemented")
    }

    override fun telemetry() = telemetry

    override fun <T : Any?> withConnection(callback: Function<SqlConnection, Mono<T>>?): Mono<T> {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> inTx(callback: Function<SqlConnection, Mono<T>>?): Mono<T> {
        TODO("Not yet implemented")
    }


}

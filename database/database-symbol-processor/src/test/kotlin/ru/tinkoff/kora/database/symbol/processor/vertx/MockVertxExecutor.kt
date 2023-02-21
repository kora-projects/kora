package ru.tinkoff.kora.database.symbol.processor.vertx

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.pgclient.impl.RowImpl
import io.vertx.sqlclient.*
import io.vertx.sqlclient.desc.ColumnDescriptor
import io.vertx.sqlclient.impl.RowDesc
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
    val rows = arrayListOf<Row>()

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
        whenever(query.executeBatch(any(), any())).thenAnswer {
            (it.arguments[1] as Handler<AsyncResult<RowSet<Row>>>).handle(Future.succeededFuture(rowSet))
        }
        whenever(rowSet.size()).thenAnswer { rows.size }
        whenever(rowSet.iterator()).thenAnswer {
            object : RowIterator<Any?> {
                private val i = rows.iterator()
                override fun remove() {
                    TODO("Not yet implemented")
                }

                override fun hasNext(): Boolean {
                    return i.hasNext()
                }

                override fun next(): Row? {
                    return i.next()
                }
            }
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


    data class MockColumn(val label: String, val value: Any)

    fun setRow(mockColumn: MockColumn) {
        this.setRow(listOf(mockColumn))
    }

    fun setRow(mockColumns: List<MockColumn>) {
        setRows(listOf(mockColumns))
    }

    fun setRows(mockRows: List<List<MockColumn>>) {
        this.rows.clear()
        for (mockRow in mockRows) {
            val labels = mockRow.asSequence()
                .map(MockColumn::label)
                .map {
                    Mockito.mock(ColumnDescriptor::class.java).also { d ->
                        whenever(d.name()).thenReturn(it)
                    }
                }
                .toList()
                .toTypedArray()
            val row = RowImpl(
                object : RowDesc(labels) {}
            )
            for (value in mockRow) {
                row.addValue(value)
            }
            this.rows.add(row)
        }
    }

}

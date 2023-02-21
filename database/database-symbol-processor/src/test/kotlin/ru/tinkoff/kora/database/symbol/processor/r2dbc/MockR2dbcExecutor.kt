package ru.tinkoff.kora.database.symbol.processor.r2dbc

import io.r2dbc.spi.*
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry
import ru.tinkoff.kora.database.r2dbc.R2dbcConnectionFactory
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Predicate

class MockR2dbcExecutor() : R2dbcConnectionFactory {
    val con: Connection = Mockito.mock(Connection::class.java)
    val statement: Statement = Mockito.mock(Statement::class.java)
    val telemetry = Mockito.mock(DataBaseTelemetry::class.java)!!
    val telemetryCtx = Mockito.mock(DataBaseTelemetry.DataBaseTelemetryContext::class.java)!!

    var rows = ArrayList<List<MockColumn>>()
    var updateCount = 0L

    init {
        reset()
    }

    fun reset() {
        rows.clear()
        Mockito.reset(con, statement, telemetry, telemetryCtx)
        whenever(telemetry.createContext(any(), any())).thenReturn(telemetryCtx)
        whenever(con.createStatement(any())).thenReturn(statement)
        whenever(statement.execute()).then {
            Flux.just(MockResult(rows, updateCount))
        }
    }


    fun setRow(mockColumn: MockColumn) {
        this.setRow(listOf(mockColumn))
    }

    fun setRow(mockColumns: List<MockColumn>) {
        setRows(listOf(mockColumns))
    }

    fun setRows(mockRows: List<List<MockColumn>>) {
        rows.clear()
        rows = ArrayList(mockRows)
    }

    data class MockColumn(val label: String, val value: Any?)

    override fun currentConnection(): Mono<Connection> {
        return Mono.fromRunnable { this.con }
    }

    override fun newConnection(): Mono<Connection> {
        return Mono.fromRunnable { this.con }
    }

    override fun telemetry() = telemetry

    override fun <T : Any?> inTx(callback: Function<Connection, Mono<T>>?): Mono<T> {
        TODO("Not yet implemented")
    }

    override fun <T> withConnection(callback: Function<Connection, Mono<T>>): Mono<T> {
        return callback.apply(con)
    }

    override fun <T : Any?> withConnectionFlux(callback: Function<Connection, Flux<T>>?): Flux<T> {
        TODO("Not yet implemented")
    }

    fun setUpdateCountResult(i: Long) {
        updateCount = i
    }


    private data class MockResult(val rows: List<List<MockColumn>>, val updateCount: Long) : Result {
        override fun getRowsUpdated(): Publisher<Long> {
            return Mono.just(updateCount)
        }

        override fun <T> map(mappingFunction: BiFunction<Row, RowMetadata, out T>): Publisher<T> {
            val mock = Mockito.mock(RowMetadata::class.java)
            whenever(mock.getColumnMetadata(Mockito.anyInt())).thenAnswer { invocation: InvocationOnMock ->
                val i = invocation.arguments[0] as Int
                val row = rows[0]
                val label: String = row[i].label
                val meta = Mockito.mock(ColumnMetadata::class.java)
                whenever(meta.name).thenReturn(label)
                meta
            }
            return Flux.fromIterable(
                rows
            ).map { columns -> MockRow(columns) }
                .map { row: MockRow -> mappingFunction.apply(row, mock) }
        }

        override fun filter(filter: Predicate<Result.Segment>): Result {
            TODO("Not yet implemented")
        }

        override fun <T : Any?> flatMap(mappingFunction: Function<Result.Segment, out Publisher<out T>>): Publisher<T> {
            TODO("Not yet implemented")
        }
    }

    private data class MockRow(val columns: List<MockColumn>) : Row {
        override fun <T> get(index: Int, type: Class<T>): T {
            if (columns[index].value is Integer) {
                return columns[index].value as T
            }
            return type.cast(columns[index].value)
        }

        override fun <T> get(name: String, type: Class<T>): T {
            for (column in columns) {
                if (column.label == name) {
                    if (column.value is Integer) {
                        return column.value as T
                    }
                    return type.cast(column.value)
                }
            }
            throw IllegalStateException()
        }

        override fun getMetadata(): RowMetadata {
            TODO("Not yet implemented")
        }
    }
}

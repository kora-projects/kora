package ru.tinkoff.kora.database.symbol.processor.jdbc

import org.mockito.kotlin.any
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import ru.tinkoff.kora.database.common.QueryContext
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry.DataBaseTelemetryContext
import ru.tinkoff.kora.database.jdbc.JdbcHelper.*
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory
import ru.tinkoff.kora.database.jdbc.RuntimeSqlException
import java.sql.*

class MockJdbcExecutor : JdbcConnectionFactory {
    val resultSet = Mockito.mock(ResultSet::class.java)

    val preparedStatement = Mockito.mock(PreparedStatement::class.java)!!
    val callableStatement = Mockito.mock(CallableStatement::class.java)!!
    val telemetry = Mockito.mock(DataBaseTelemetry::class.java)!!
    val telemetryCtx = Mockito.mock(DataBaseTelemetryContext::class.java)!!
    val mockConnection = Mockito.mock(Connection::class.java)!!

    fun reset() {
        Mockito.reset(resultSet, preparedStatement, callableStatement, mockConnection, telemetry)
        Mockito.`when`(mockConnection.prepareCall(ArgumentMatchers.anyString())).thenReturn(callableStatement)
        Mockito.`when`(mockConnection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(preparedStatement)
        Mockito.`when`(telemetry.createContext(any(), any())).thenReturn(telemetryCtx)
    }

    init {
        reset()
    }

    @Throws(RuntimeSqlException::class)
    override fun <T> withConnection(callback: SqlFunction1<Connection, T>): T {
        return try {
            callback.apply(mockConnection)
        } catch (e: SQLException) {
            throw RuntimeSqlException(e)
        }
    }

    override fun currentConnection() = mockConnection!!

    override fun newConnection(): Connection {
        TODO("Not yet implemented")
    }

    override fun telemetry() = this.telemetry!!
}

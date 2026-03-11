package io.koraframework.database.symbol.processor.jdbc

import io.opentelemetry.api.trace.Span
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import io.koraframework.database.common.telemetry.DataBaseObservation
import io.koraframework.database.common.telemetry.DataBaseTelemetry
import io.koraframework.database.jdbc.ConnectionContext
import io.koraframework.database.jdbc.JdbcConnectionFactory
import io.koraframework.database.jdbc.JdbcHelper.SqlFunction1
import io.koraframework.database.jdbc.RuntimeSqlException
import java.sql.*

class MockJdbcExecutor : JdbcConnectionFactory {
    val resultSet = Mockito.mock(ResultSet::class.java)

    val preparedStatement = Mockito.mock(PreparedStatement::class.java)!!
    val callableStatement = Mockito.mock(CallableStatement::class.java)!!
    val telemetry = Mockito.mock(DataBaseTelemetry::class.java)!!
    val telemetryCtx = Mockito.mock(DataBaseObservation::class.java)!!
    val mockConnection = Mockito.mock(Connection::class.java)!!
    val mockConnectionContext = ConnectionContext(mockConnection)

    fun reset() {
        Mockito.reset(resultSet, preparedStatement, callableStatement, mockConnection, telemetry)
        whenever(mockConnection.prepareCall(ArgumentMatchers.anyString())).thenReturn(callableStatement)
        whenever(mockConnection.prepareStatement(ArgumentMatchers.anyString())).thenReturn(preparedStatement)
        whenever(mockConnection.prepareStatement(ArgumentMatchers.anyString(), any<Int>())).thenReturn(preparedStatement)
        whenever(telemetry.observe(any())).thenReturn(telemetryCtx)
        whenever(telemetryCtx.span()).thenReturn(Span.getInvalid())
        whenever(preparedStatement.executeQuery()).thenReturn(resultSet)
    }

    init {
        reset()
    }

    override fun <T> withConnection(callback: SqlFunction1<Connection, T>): T {
        return try {
            callback.apply(mockConnection)
        } catch (e: SQLException) {
            throw RuntimeSqlException(e)
        }
    }

    override fun currentConnection() = mockConnection!!

    override fun currentConnectionContext() = mockConnectionContext

    override fun newConnection(): Connection {
        TODO("Not yet implemented")
    }

    override fun telemetry() = this.telemetry!!
}

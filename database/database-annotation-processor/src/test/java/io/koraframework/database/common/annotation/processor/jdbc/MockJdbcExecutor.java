package io.koraframework.database.common.annotation.processor.jdbc;

import org.mockito.Mockito;
import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseTelemetry;
import io.koraframework.database.jdbc.ConnectionContext;
import io.koraframework.database.jdbc.JdbcExecutor;
import io.koraframework.database.jdbc.UncheckedSqlException;

import java.sql.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class MockJdbcExecutor implements JdbcExecutor {
    public final ResultSet resultSet = Mockito.mock(ResultSet.class);
    public final PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
    public final CallableStatement callableStatement = Mockito.mock(CallableStatement.class);
    public final Connection mockConnection = Mockito.mock(Connection.class);
    public final ConnectionContext mockConnectionContext = new ConnectionContext(mockConnection);

    public void reset() {
        Mockito.reset(resultSet, preparedStatement, callableStatement, mockConnection);
        try {
            when(mockConnection.prepareCall(anyString())).thenReturn(callableStatement);
            when(mockConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(preparedStatement.getResultSet()).thenReturn(resultSet);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public MockJdbcExecutor() {
        this.reset();
    }

    @Override
    public <T> T withContext(SqlFunction<ConnectionContext, T> callback) throws UncheckedSqlException {
        try {
            return callback.apply(mockConnectionContext);
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    public Connection connectionCurrent() {
        return mockConnection;
    }

    @Override
    public ConnectionContext currentContext() {
        return mockConnectionContext;
    }

    @Override
    public Connection acquireConnection() {
        return mockConnection;
    }

    @Override
    public DatabaseTelemetry telemetry() {
        return NoopDatabaseTelemetry.INSTANCE;
    }

}

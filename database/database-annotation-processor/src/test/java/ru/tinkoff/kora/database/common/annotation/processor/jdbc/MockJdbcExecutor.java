package ru.tinkoff.kora.database.common.annotation.processor.jdbc;

import org.mockito.Mockito;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;
import ru.tinkoff.kora.database.jdbc.JdbcConnectionFactory;
import ru.tinkoff.kora.database.jdbc.JdbcHelper;
import ru.tinkoff.kora.database.jdbc.RuntimeSqlException;

import javax.annotation.Nullable;
import java.sql.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class MockJdbcExecutor implements JdbcConnectionFactory {
    public final ResultSet resultSet = Mockito.mock(ResultSet.class);
    public final PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
    public final CallableStatement callableStatement = Mockito.mock(CallableStatement.class);
    public final Connection mockConnection = Mockito.mock(Connection.class);

    public void reset() {
        Mockito.reset(resultSet, preparedStatement, callableStatement, mockConnection);
        try {
            when(mockConnection.prepareCall(anyString())).thenReturn(callableStatement);
            when(mockConnection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(preparedStatement.getResultSet()).thenReturn(resultSet);
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    public MockJdbcExecutor() {
        this.reset();
    }

    @Override
    public <T> T withConnection(JdbcHelper.SqlFunction1<Connection, T> callback) throws RuntimeSqlException {
        try {
            return callback.apply(mockConnection);
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }


    @Override
    public Connection currentConnection() {
        return mockConnection;
    }

    @Override
    public Connection newConnection() {
        return mockConnection;
    }

    @Override
    public DataBaseTelemetry telemetry() {
        return new DataBaseTelemetry() {
            @Nullable
            @Override
            public Object getMetricRegistry() {
                return null;
            }

            @Override
            public DataBaseTelemetryContext createContext(Context context, QueryContext query) {
                return Mockito.mock(DataBaseTelemetryContext.class);
            }
        };
    }

}

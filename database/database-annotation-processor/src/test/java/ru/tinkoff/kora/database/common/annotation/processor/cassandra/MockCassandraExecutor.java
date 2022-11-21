package ru.tinkoff.kora.database.common.annotation.processor.cassandra;

import com.datastax.dse.driver.api.core.cql.reactive.ReactiveResultSet;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import org.mockito.Mockito;
import ru.tinkoff.kora.database.cassandra.CassandraConnectionFactory;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;

import java.util.Iterator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class MockCassandraExecutor implements CassandraConnectionFactory {

    public final ResultSet resultSet = Mockito.mock(ResultSet.class);
    public final AsyncResultSet asyncResultSet = Mockito.mock(AsyncResultSet.class);
    public final ReactiveResultSet reactiveResultSet = Mockito.mock(ReactiveResultSet.class);
    public final BoundStatementBuilder boundStatementBuilder = Mockito.mock(BoundStatementBuilder.class);
    public final PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
    public final BoundStatement boundStatement = Mockito.mock(BoundStatement.class);
    public final CqlSession mockSession = Mockito.mock(CqlSession.class);
    public final Iterator<Row> iterator = Mockito.mock(Iterator.class);
    public final Row row = Mockito.mock(Row.class);
    public final DataBaseTelemetry telemetry = Mockito.mock(DataBaseTelemetry.class);
    public final DataBaseTelemetry.DataBaseTelemetryContext telemetryCtx = Mockito.mock(DataBaseTelemetry.DataBaseTelemetryContext.class);

    public MockCassandraExecutor() {
        reset();
    }

    public void reset() {
        Mockito.reset(asyncResultSet, reactiveResultSet, boundStatementBuilder, preparedStatement, mockSession, iterator, row, telemetry, telemetryCtx);
        when(boundStatementBuilder.build()).thenReturn(boundStatement);
        when(asyncResultSet.currentPage()).thenReturn(() -> iterator);
        when(mockSession.execute(any(Statement.class))).thenReturn(resultSet);
        when(resultSet.iterator()).thenReturn(iterator);
        when(resultSet.one()).thenReturn(row);
        when(iterator.next()).thenReturn(row);
        when(mockSession.prepare(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.boundStatementBuilder()).thenReturn(boundStatementBuilder);
        when(boundStatementBuilder.setExecutionProfileName(any())).thenReturn(boundStatementBuilder);
        when(boundStatementBuilder.build()).thenReturn(boundStatement);
        when(telemetry.createContext(any(), any())).thenReturn(this.telemetryCtx);
        when(mockSession.executeReactive(any(Statement.class))).thenReturn(reactiveResultSet);
    }


    @Override
    public CqlSession currentSession() {
        return mockSession;
    }

    @Override
    public DataBaseTelemetry telemetry() {
        return this.telemetry;
    }
}

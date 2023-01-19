package ru.tinkoff.kora.database.common.annotation.processor.vertx;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.pgclient.impl.RowImpl;
import io.vertx.sqlclient.*;
import io.vertx.sqlclient.desc.ColumnDescriptor;
import io.vertx.sqlclient.impl.RowDesc;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;
import ru.tinkoff.kora.database.vertx.VertxConnectionFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class MockVertxExecutor implements VertxConnectionFactory {
    public final SqlConnection connection = Mockito.mock(SqlConnection.class);
    public final PreparedStatement stmt = Mockito.mock(PreparedStatement.class);
    public final PreparedQuery<RowSet<Row>> query = Mockito.mock(PreparedQuery.class);
    public final RowSet<Row> rowSet = Mockito.mock(RowSet.class);
    public final List<Row> rows = new ArrayList<>();
    public final DataBaseTelemetry telemetry = Mockito.mock(DataBaseTelemetry.class);
    public final DataBaseTelemetry.DataBaseTelemetryContext telemetryContext = Mockito.mock(DataBaseTelemetry.DataBaseTelemetryContext.class);

    public MockVertxExecutor() {
        reset();
    }

    public void reset() {
        Mockito.reset(connection, query, rowSet, telemetry, telemetryContext, stmt);
        when(connection.preparedQuery(anyString())).thenReturn(query);
        doAnswer(invocation -> {
            var handler = (Handler<AsyncResult<PreparedStatement>>) invocation.getArgument(1);
            handler.handle(Future.succeededFuture(stmt));
            return connection;
        }).when(connection).prepare(anyString(), any(Handler.class));
        when(stmt.query()).thenReturn(query);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                var handler = (Handler<AsyncResult<RowSet<Row>>>) invocation.getArgument(1);
                handler.handle(Future.succeededFuture(rowSet));
                return null;
            }
        }).when(query).execute(any(Tuple.class), any());
        when(query.execute(any(Tuple.class))).thenReturn(Future.succeededFuture(rowSet));
        when(query.executeBatch(any())).thenReturn(Future.succeededFuture());
        when(rowSet.iterator()).thenAnswer(invocation -> new RowIterator<>() {
            private final Iterator<Row> i = rows.iterator();

            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public Row next() {
                return i.next();
            }
        });
        when(rowSet.size()).thenAnswer(i -> rows.size());
        when(telemetry.createContext(any(), any())).thenReturn(telemetryContext);
    }

    public record MockColumn(String label, Object value) {}

    public void setRow(MockColumn mockColumn) {
        this.setRow(List.of(mockColumn));
    }

    public void setRow(List<MockColumn> mockColumns) {
        this.setRows(List.of(mockColumns));
    }

    public void setRows(List<List<MockColumn>> mockRows) {
        this.rows.clear();
        for (var mockRow : mockRows) {
            var labels = mockRow.stream()
                .map(MockColumn::label)
                .map(l -> {
                    var d = Mockito.mock(ColumnDescriptor.class);
                    when(d.name()).thenReturn(l);
                    return d;
                })
                .toArray(ColumnDescriptor[]::new);
            var row = new RowImpl(
                new RowDesc(labels) {}
            );
            for (var mockColumn : mockRow) {
                row.addValue(mockColumn.value());
            }
            this.rows.add(row);
        }
    }

    @Override
    public SqlConnection currentConnection() {
        return connection;
    }

    @Override
    public CompletionStage<SqlConnection> newConnection() {
        return null;
    }

    @Override
    public Pool pool() {
        return null;
    }

    @Override
    public DataBaseTelemetry telemetry() {
        return this.telemetry;
    }

    @Override
    public <T> Mono<T> withConnection(Function<SqlConnection, Mono<T>> callback) {
        return callback.apply(connection);
    }

    @Override
    public <T> Mono<T> inTx(Function<SqlConnection, Mono<T>> callback) {
        return callback.apply(connection);
    }
}

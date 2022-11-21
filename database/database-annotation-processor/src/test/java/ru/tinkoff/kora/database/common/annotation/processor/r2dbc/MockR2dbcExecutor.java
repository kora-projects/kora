package ru.tinkoff.kora.database.common.annotation.processor.r2dbc;

import io.r2dbc.spi.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;
import ru.tinkoff.kora.database.r2dbc.R2dbcConnectionFactory;
import ru.tinkoff.kora.database.r2dbc.mapper.result.R2dbcResultFluxMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class MockR2dbcExecutor implements R2dbcConnectionFactory {
    public final Connection con = Mockito.mock(Connection.class);
    public final Statement statement = Mockito.mock(Statement.class);
    public final DataBaseTelemetry telemetry = Mockito.mock(DataBaseTelemetry.class);
    public final DataBaseTelemetry.DataBaseTelemetryContext telemetryContext = Mockito.mock(DataBaseTelemetry.DataBaseTelemetryContext.class);
    public List<List<MockColumn>> rows = new ArrayList<>();

    public void setRow(MockColumn mockColumn) {
        this.setRow(List.of(mockColumn));
    }

    public void setRow(List<MockColumn> mockColumns) {
        this.setRows(List.of(mockColumns));
    }

    public void setRows(List<List<MockColumn>> mockRows) {
        this.rows.clear();
        this.rows = new ArrayList<>(mockRows);
    }

    public void reset() {
        Mockito.reset(con, statement, telemetry, telemetryContext);
        rows = new ArrayList<>();
        when(con.createStatement(any())).thenReturn(statement);
        when(statement.execute()).thenReturn((Publisher) Flux.defer(() -> Flux.just(new MockResult(this.rows))));
        when(telemetry.createContext(any(), any())).thenReturn(telemetryContext);
    }

    public record MockColumn(String label, Object value) {}

    public <T> Mono<T> queryMono(Connection connection, QueryContext query, Consumer<Statement> statementSetter, R2dbcResultFluxMapper<T, Mono<T>> mapper) {
        this.con.createStatement(query.sql());
        statementSetter.accept(this.statement);
        return mapper.apply(Flux.just(new MockResult(this.rows)));
    }

    public <T> Flux<T> queryFlux(Connection connection, QueryContext query, Consumer<Statement> statementSetter, R2dbcResultFluxMapper<T, Flux<T>> mapper) {
        this.con.createStatement(query.sql());
        statementSetter.accept(this.statement);
        return mapper.apply(Flux.empty());
    }

    public Mono<Long> executeBatch(Connection connection, QueryContext query, int batchSize, BiConsumer<Integer, Statement> statementSetter) {
        this.con.createStatement(query.sql());
        for (int i = 0; i < batchSize; i++) {
            statementSetter.accept(i, this.statement);
            this.statement.add();
        }
        return Mono.just(0L);
    }

    @Override
    public Mono<Connection> currentConnection() {
        return null;
    }

    @Override
    public Mono<Connection> newConnection() {
        return null;
    }

    @Override
    public DataBaseTelemetry telemetry() {
        return this.telemetry;
    }

    @Override
    public <T> Mono<T> inTx(Function<Connection, Mono<T>> callback) {
        return null;
    }

    @Override
    public <T> Mono<T> withConnection(Function<Connection, Mono<T>> callback) {
        return callback.apply(con);
    }


    public <T> Mono<T> inTx(Connection connection, Function<Connection, Mono<T>> callback) {
        return callback.apply(connection);
    }

    public <T> Flux<T> withConnectionFlux(Function<Connection, Flux<T>> callback) {
        return callback.apply(con);
    }

    public <T> Flux<T> inTxFlux(Connection connection, Function<Connection, Flux<T>> callback) {
        return callback.apply(connection);
    }

    private record MockResult(List<List<MockColumn>> rows) implements Result {

        @Override
        public Publisher<Long> getRowsUpdated() {
            return Mono.just(0L);
        }

        @Override
        public <T> Publisher<T> map(BiFunction<Row, RowMetadata, ? extends T> mappingFunction) {
            var mock = Mockito.mock(RowMetadata.class);
            when(mock.getColumnMetadata(Mockito.anyInt())).thenAnswer(invocation -> {
                var i = (Integer) invocation.getArguments()[0];
                var row = rows.get(0);
                var label = row.get(i).label;
                var meta = Mockito.mock(ColumnMetadata.class);
                when(meta.getName()).thenReturn(label);
                return meta;
            });
            return Flux.fromIterable(this.rows).map(MockRow::new).map(row -> mappingFunction.apply(row, mock));
        }

        @Override
        public Result filter(Predicate<Segment> filter) {
            return this;
        }

        @Override
        public <T> Publisher<T> flatMap(Function<Segment, ? extends Publisher<? extends T>> mappingFunction) {
            throw new IllegalStateException();
        }
    }

    private record MockRow(List<MockColumn> columns) implements Row {
        @Override
        public <T> T get(int index, Class<T> type) {
            return type.cast(columns.get(index).value);
        }

        @Override
        public <T> T get(String name, Class<T> type) {
            for (var column : columns) {
                if (column.label.equals(name)) {
                    return type.cast(column.value);
                }
            }
            throw new IllegalStateException();
        }

        @Override
        public RowMetadata getMetadata() {
            throw new IllegalStateException();
        }
    }
}

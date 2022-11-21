package ru.tinkoff.kora.database.vertx.query;

import io.vertx.sqlclient.SqlConnection;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class PreparedVertxQuery {
    /*

    private final SqlConnection connection;
    private final String profile;
    private final DataBaseTelemetry telemetry;
    private final QueryContext queryContext;

    private final CassandraStatementSetter statementSetter;

    public PreparedVertxQuery(CqlSession connection, @Nullable String profile, DataBaseTelemetry telemetry, QueryContext queryContext, CassandraStatementSetter statementSetter) {
        this.connection = connection;
        this.profile = profile;
        this.telemetry = telemetry;
        this.queryContext = queryContext;
        this.statementSetter = statementSetter;
    }

    @Nullable
    public <T> T fetch(CassandraResultSetMapper<T> statementMapper) {
        var telemetry = this.telemetry.createContext(Context.current(), this.queryContext);
        var b = this.connection.prepare(this.queryContext.sql())
            .boundStatementBuilder()
            .setExecutionProfileName(this.profile);
        try {
            var stmt = this.statementSetter.apply(b);
            var rs = this.connection.execute(stmt);
            var result = statementMapper.apply(rs);
            telemetry.close(null);
            return result;
        } catch (Exception e) {
            telemetry.close(e);
            throw e;
        }
    }

    @Nullable
    public <T> Mono<T> fetchMono(CassandraReactiveResultSetMapper<T, Mono<T>> statementMapper) {
        return Mono.deferContextual(ctx -> {
            var c = Context.current(ctx).fork();
            var telemetry = this.telemetry.createContext(c, this.queryContext);
            return Mono.fromCompletionStage(() -> this.connection.prepareAsync(this.queryContext.sql()))
                .flatMap(ps -> {
                    var b = ps.boundStatementBuilder()
                        .setExecutionProfileName(this.profile);
                    var stmt = this.statementSetter.apply(b);
                    var rs = this.connection.executeReactive(stmt);
                    return statementMapper.apply(rs);
                })
                .doOnEach(e -> {
                    if (e.isOnComplete()) {
                        telemetry.close(null);
                    } else if (e.isOnError()) {
                        telemetry.close(e.getThrowable());
                    }
                })
                .contextWrite(c::inject);
        });
    }

    @Nullable
    public <T> Mono<T> fetchMono(CassandraRowMapper<T> statementMapper) {
        return this.fetchMono((ReactiveResultSet rrs) -> Mono.from(rrs).map(statementMapper::apply));
    }

    @Nullable
    public <T> Flux<T> fetchFlux(CassandraReactiveResultSetMapper<T, Flux<T>> statementMapper) {
        return Flux.deferContextual(ctx -> {
            var c = Context.current(ctx).fork();
            var telemetry = this.telemetry.createContext(c, this.queryContext);
            return Mono.fromCompletionStage(() -> this.connection.prepareAsync(this.queryContext.sql()))
                .flatMapMany(ps -> {
                    var b = ps.boundStatementBuilder();
                    var stmt = this.statementSetter.apply(b)
                        .setExecutionProfileName(this.profile);
                    var rs = this.connection.executeReactive(stmt);
                    return statementMapper.apply(rs);
                })
                .doOnEach(e -> {
                    if (e.isOnComplete()) {
                        telemetry.close(null);
                    } else if (e.isOnError()) {
                        telemetry.close(e.getThrowable());
                    }
                })
                .contextWrite(c::inject);
        });
    }

    @Nullable
    public <T> Flux<T> fetchFlux(CassandraRowMapper<T> rowMapper) {
        return this.fetchFlux((ReactiveResultSet rs) -> Flux.from(rs).map(rowMapper::apply));
    }

    public <T> List<T> fetchList(CassandraRowMapper<T> rowMapper) {
        return this.fetch((ResultSet rs) -> {
            var list = new ArrayList<T>();
            for (var row : rs) {
                list.add(rowMapper.apply(row));
            }
            return list;
        });
    }

    public <T> Mono<List<T>> fetchListMono(CassandraRowMapper<T> rowMapper) {
        return this.fetchMono((ReactiveResultSet rs) -> Flux.from(rs).map(rowMapper::apply).collectList());
    }

    public <T> Stream<T> fetchStream(CassandraRowMapper<T> rowMapper) {
        var telemetry = this.telemetry.createContext(Context.current(), this.queryContext);
        try {
            var b = this.connection.prepare(queryContext.sql()).boundStatementBuilder();
            var stmt = this.statementSetter.apply(b)
                .setExecutionProfileName(this.profile);
            var rs = this.connection.execute(stmt);
            return StreamSupport.stream(rs.spliterator(), false)
                .map(rowMapper::apply)
                .onClose(() -> telemetry.close(null));
        } catch (Exception e) {
            telemetry.close(e);
            throw e;
        }
    }

    public <T> Optional<T> fetchOptional(CassandraRowMapper<T> rowMapper) {
        return this.fetch((ResultSet rs) -> {
            for (var row : rs) {
                return Optional.ofNullable(rowMapper.apply(row));
            }
            return Optional.empty();
        });
    }

    @Nullable
    public <T> T fetchOne(CassandraRowMapper<T> rowMapper) {
        return this.fetch((ResultSet rs) -> {
            var row = rs.one();
            if (row != null) {
                return rowMapper.apply(row);
            } else {
                return null;
            }
        });
    }

    public boolean execute() {
        return Objects.requireNonNull(this.fetch(ResultSet::wasApplied));
    }

    public Mono<Boolean> executeReactive() {
        return Objects.requireNonNull(this.fetchMono((ReactiveResultSet rs) -> Mono.from(rs.wasApplied())));
    }

     */
}


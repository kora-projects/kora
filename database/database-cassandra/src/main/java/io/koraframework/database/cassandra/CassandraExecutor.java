package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.database.cassandra.mapper.result.CassandraResultSetMapper;
import io.koraframework.database.cassandra.mapper.result.CassandraRowMapper;
import io.koraframework.database.common.QueryContext;
import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import io.opentelemetry.context.Context;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * <b>Русский</b>: Фабрика соединений Cassandra которая позволяет выполнять запросы в ручном режиме.
 * <hr>
 * <b>English</b>: Cassandra's connection factory that allows you to fulfil requests in manual mode.
 *
 * @see CassandraRepository
 */
@SuppressWarnings("overloads")
public interface CassandraExecutor {

    CqlSession currentSession();

    DatabaseTelemetry telemetry();

    default <T> T query(QueryContext queryContext, Function<PreparedStatement, T> callback) {
        var observation = this.telemetry().observe(queryContext);
        return ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .call(() -> {
                observation.observeConnection();
                var stmt = this.currentSession().prepare(queryContext.sql());
                observation.observeStatement();
                try {
                    var result = callback.apply(stmt);
                    return result;
                } catch (Exception e) {
                    observation.observeError(e);
                    throw e;
                } finally {
                    observation.end();
                }
            });
    }

    default <T> T query(CassandraQuery query, Function<BoundStatement, T> callback) {
        var queryContext = new QueryContext(query.sourceCql(), query.cql());
        var observation = this.telemetry().observe(queryContext);
        return ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .call(() -> {
                observation.observeConnection();
                var stmt = query.prepare(this.currentSession());
                observation.observeStatement();
                try {
                    var result = callback.apply(stmt);
                    return result;
                } catch (Exception e) {
                    observation.observeError(e);
                    throw e;
                } finally {
                    observation.end();
                }
            });
    }

    default <T> T query(CassandraQuery query, CassandraResultSetMapper<T> mapper) {
        return this.query(query, (Function<BoundStatement, T>) statement -> mapper.apply(this.currentSession().execute(statement)));
    }

    @Nullable
    default <T> T queryOne(CassandraQuery query, CassandraRowMapper<T> mapper) {
        return this.query(query, CassandraResultSetMapper.singleResultSetMapper(mapper));
    }

    default <T> Optional<T> queryOptional(CassandraQuery query, CassandraRowMapper<T> mapper) {
        return this.query(query, CassandraResultSetMapper.optionalResultSetMapper(mapper));
    }

    default <T> List<T> queryList(CassandraQuery query, CassandraRowMapper<T> mapper) {
        return this.query(query, CassandraResultSetMapper.listResultSetMapper(mapper));
    }
}

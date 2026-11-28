package io.koraframework.database.cassandra;

import com.datastax.oss.driver.api.core.*;
import com.datastax.oss.driver.api.core.cql.*;
import io.koraframework.common.context.*;
import io.koraframework.common.telemetry.*;
import io.koraframework.database.cassandra.mapper.result.*;
import io.koraframework.database.common.*;
import io.koraframework.database.common.telemetry.*;
import org.jspecify.annotations.*;

import java.util.*;
import java.util.function.*;

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
        KoraContext current = KoraContext.current();
        KoraContext updated = Observation.attach(current != null ? current : KoraContext.empty(), observation);

        return KoraContext.runWith(updated, new PreparedStatementQueryRunner<>(this, observation, queryContext, callback));
    }

    default <T> T query(CassandraQuery query, Function<BoundStatement, T> callback) {
        var queryContext = new QueryContext(query.sourceCql(), query.cql());
        var observation = this.telemetry().observe(queryContext);
        KoraContext current = KoraContext.current();
        KoraContext updated = Observation.attach(current != null ? current : KoraContext.empty(), observation);

        return KoraContext.runWith(updated, new BoundStatementQueryRunner<>(this, observation, query, callback));
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

    final class PreparedStatementQueryRunner<T> implements Supplier<T> {
        private final CassandraExecutor executor;
        private final DatabaseObservation observation;
        private final QueryContext queryContext;
        private final Function<PreparedStatement, T> callback;

        public PreparedStatementQueryRunner(CassandraExecutor executor, DatabaseObservation observation, QueryContext queryContext, Function<PreparedStatement, T> callback) {
            this.executor = executor;
            this.observation = observation;
            this.queryContext = queryContext;
            this.callback = callback;
        }

        @Override
        public T get() {
            observation.observeConnection();
            var stmt = executor.currentSession().prepare(queryContext.sql());
            observation.observeStatement();
            try {
                return callback.apply(stmt);
            } catch (Exception e) {
                observation.observeError(e);
                throw e;
            } finally {
                observation.end();
            }
        }
    }

    final class BoundStatementQueryRunner<T> implements Supplier<T> {
        private final CassandraExecutor executor;
        private final DatabaseObservation observation;
        private final CassandraQuery query;
        private final Function<BoundStatement, T> callback;

        public BoundStatementQueryRunner(CassandraExecutor executor, DatabaseObservation observation, CassandraQuery query, Function<BoundStatement, T> callback) {
            this.executor = executor;
            this.observation = observation;
            this.query = query;
            this.callback = callback;
        }

        @Override
        public T get() {
            observation.observeConnection();
            var stmt = query.prepare(executor.currentSession());
            observation.observeStatement();
            try {
                return callback.apply(stmt);
            } catch (Exception e) {
                observation.observeError(e);
                throw e;
            } finally {
                observation.end();
            }
        }
    }
}

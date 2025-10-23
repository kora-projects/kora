package ru.tinkoff.kora.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import io.opentelemetry.context.Context;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;

import java.util.function.Function;

/**
 * <b>Русский</b>: Фабрика соединений Cassandra которая позволяет выполнять запросы в ручном режиме.
 * <hr>
 * <b>English</b>: Cassandra's connection factory that allows you to fulfil requests in manual mode.
 *
 * @see CassandraRepository
 */
public interface CassandraConnectionFactory {

    CqlSession currentSession();

    DataBaseTelemetry telemetry();

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
}

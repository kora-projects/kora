package ru.tinkoff.kora.database.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.database.common.QueryContext;
import ru.tinkoff.kora.database.common.annotation.Repository;
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
        var telemetry = this.telemetry().createContext(Context.current(), queryContext);
        var stmt = this.currentSession().prepare(queryContext.sql());
        try {
            var result = callback.apply(stmt);
            telemetry.close(null);
            return result;
        } catch (Exception e) {
            telemetry.close(e);
            throw e;
        }
    }
}

package ru.tinkoff.kora.database.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

import javax.annotation.Nullable;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class JdbcDatabase implements Lifecycle, Wrapped<DataSource>, JdbcConnectionFactory {
    private final Context.Key<Connection> connectionKey = new Context.Key<>() {
        @Override
        protected Connection copy(Connection object) {
            return null;
        }
    };

    private final JdbcDatabaseConfig databaseConfig;
    private final HikariDataSource dataSource;
    private final DataBaseTelemetry telemetry;

    public JdbcDatabase(JdbcDatabaseConfig databaseConfig, DataBaseTelemetryFactory telemetryFactory) {
        this(databaseConfig, telemetryFactory == null ? null : telemetryFactory.get(databaseConfig.poolName(), "", databaseConfig.username()));
    }

    public JdbcDatabase(JdbcDatabaseConfig databaseConfig, DataBaseTelemetry telemetry) {
        this.databaseConfig = databaseConfig;
        this.telemetry = telemetry;
        this.dataSource = new HikariDataSource(JdbcDatabaseConfig.toHikariConfig(this.databaseConfig));
        if (telemetry != null) this.dataSource.setMetricRegistry(telemetry.getMetricRegistry());
    }

    @Override
    public Mono<Void> init() {
        return ReactorUtils.ioMono(() -> {
            try (var connection = this.dataSource.getConnection()) {
                connection.isValid(1000);
            } catch (SQLException e) {
                throw Exceptions.propagate(e);
            }
        });
    }

    @Override
    public Mono<Void> release() {
        return ReactorUtils.ioMono(this.dataSource::close);
    }

    @Override
    public DataSource value() {
        return this.dataSource;
    }

    @Nullable
    @Override
    public Connection newConnection() {
        try {
            return this.dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        }
    }

    @Override
    public DataBaseTelemetry telemetry() {
        return this.telemetry;
    }

    @Nullable
    @Override
    public Connection currentConnection() {
        var ctx = Context.current();
        return ctx.get(this.connectionKey);
    }

    @Override
    public <T> T withConnection(JdbcHelper.SqlFunction1<Connection, T> callback) throws RuntimeSqlException {
        var ctx = Context.current();

        var currentConnection = ctx.get(this.connectionKey);
        if (currentConnection != null) {
            try {
                return callback.apply(currentConnection);
            } catch (SQLException e) {
                throw new RuntimeSqlException(e);
            }
        }
        try (var connection = ctx.set(this.connectionKey, this.newConnection())) {
            return callback.apply(connection);
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        } finally {
            ctx.remove(this.connectionKey);
        }
    }
}

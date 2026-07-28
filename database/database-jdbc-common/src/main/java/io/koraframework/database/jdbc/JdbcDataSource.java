package io.koraframework.database.jdbc;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.common.readiness.ReadinessProbeFailure;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.database.common.telemetry.DatabaseTelemetry;
import io.koraframework.database.common.telemetry.DatabaseTelemetryFactory;
import io.koraframework.database.jdbc.exception.UncheckedSqlException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class JdbcDataSource implements Lifecycle, Wrapped<DataSource>, JdbcExecutor, ReadinessProbe {

    private static final Logger logger = LoggerFactory.getLogger(JdbcDataSource.class);

    private final ScopedValue<ConnectionContext> connectionContext = ScopedValue.newInstance();

    private final JdbcDatabaseConfig databaseConfig;
    private final String dataSourceName;
    private final DataSource dataSource;
    private final DatabaseTelemetry telemetry;
    private final Runnable closable;

    public JdbcDataSource(DataSource dataSource,
                          JdbcDatabaseConfig databaseConfig,
                          DatabaseTelemetryFactory telemetryFactory,
                          Runnable closable) {
        var jdbcUrl = databaseConfig.jdbcUrl();
        var jdbcDatabase = jdbcUrl.substring(5, jdbcUrl.indexOf(":", 5));
        this.telemetry = telemetryFactory.get(databaseConfig.telemetry(), databaseConfig.poolName(), jdbcDatabase);
        this.dataSourceName = dataSource.getClass().getSimpleName();
        this.databaseConfig = databaseConfig;
        this.dataSource = dataSource;
        this.closable = closable;
    }

    @Override
    public void init() throws SQLException {
        if (this.databaseConfig.initializationFailTimeout() != null) {
            logger.debug("{} pool '{}' starting...", dataSourceName, databaseConfig.poolName());
            var started = System.nanoTime();

            try (var connection = this.dataSource.getConnection()) {
                connection.isValid((int) this.databaseConfig.initializationFailTimeout().toMillis());
            } catch (SQLException e) {
                throw new RuntimeException("{} pool '%s' failed to start, due to: %s".formatted(
                    dataSourceName, databaseConfig.poolName(), e.getMessage()), e);
            }

            logger.info("{} pool '{}' started in {}",
                dataSourceName, databaseConfig.poolName(), TimeUtils.tookForLogging(started));
        } else {
            logger.debug("{} pool '{}' initialization is skipped cause `initializationFailTimeout` is not specified...",
                dataSourceName, databaseConfig.poolName());
        }
    }

    @Override
    public void release() {
        logger.debug("{} pool '{}' stopping...", dataSourceName, databaseConfig.poolName());
        var started = System.nanoTime();

        closable.run();

        logger.info("{} pool '{}' stopped in {}", dataSourceName, databaseConfig.poolName(), TimeUtils.tookForLogging(started));
    }

    @Override
    public DataSource value() {
        return this.dataSource;
    }

    @Nullable
    @Override
    public Connection acquireConnection() {
        try {
            return this.dataSource.getConnection();
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @Override
    public DatabaseTelemetry telemetry() {
        return this.telemetry;
    }

    @Nullable
    public Connection connectionCurrent() {
        if (this.connectionContext.isBound()) {
            return this.connectionContext.get().connection();
        }
        return null;
    }

    @Nullable
    public ConnectionContext currentContext() {
        if (this.connectionContext.isBound()) {
            return this.connectionContext.get();
        }
        return null;
    }

    @Override
    public <T> T withContext(SqlFunction<ConnectionContext, T> callback) throws UncheckedSqlException {
        if (this.connectionContext.isBound()) {
            try {
                return callback.apply(this.connectionContext.get());
            } catch (SQLException e) {
                throw new UncheckedSqlException(e);
            }
        }

        try (var connection = this.acquireConnection()) {
            var context = new ConnectionContext(connection);
            return ScopedValue.where(this.connectionContext, context)
                .call(() -> callback.apply(context));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        }
    }

    @Override
    public ReadinessProbeFailure probe() throws Exception {
        if (this.databaseConfig.readinessProbe()) {
            try (var c = this.dataSource.getConnection()) {
                c.isValid((int) this.databaseConfig.validationTimeout().toMillis());
            }
        }
        return null;
    }
}

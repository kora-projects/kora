package io.koraframework.database.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.RefreshListener;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.Configurer;
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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class JdbcDataSource implements Lifecycle, Wrapped<DataSource>, JdbcExecutor, ReadinessProbe, RefreshListener {

    private static final Logger logger = LoggerFactory.getLogger(JdbcDataSource.class);

    private final JdbcDatabaseConfig databaseConfig;
    private final HikariDataSource dataSource;
    private final DatabaseTelemetry telemetry;
    private final ScopedValue<ConnectionContext> connectionContext = ScopedValue.newInstance();
    private final AtomicBoolean driverMetricsBound = new AtomicBoolean(false);

    public JdbcDataSource(JdbcDatabaseConfig config, DatabaseTelemetryFactory telemetryFactory, @Nullable Configurer<HikariConfig> configurer) {
        this.databaseConfig = Objects.requireNonNull(config);
        var jdbcUrl = config.jdbcUrl();
        var jdbcDatabase = jdbcUrl.substring(5, jdbcUrl.indexOf(":", 5));
        this.telemetry = telemetryFactory.get(
            config.telemetry(),
            config.poolName(),
            jdbcDatabase
        );
        this.dataSource = new HikariDataSource(JdbcDatabaseConfig.toHikariConfig(this.databaseConfig, configurer));
    }

    /**
     * Driver metrics are bound here rather than in the constructor: on a refresh the replacement pool
     * is created while the previous one still holds the meters of the same pool name, so its
     * registration would be ignored and then dropped when the previous pool closes. This callback runs
     * once the replaced nodes are released, which is the first moment the meters are free.
     */
    @Override
    public void graphRefreshed() {
        if (!this.databaseConfig.telemetry().metrics().driverMetrics()) {
            return;
        }
        if (this.driverMetricsBound.compareAndSet(false, true)) {
            this.dataSource.setMetricRegistry(this.telemetry.meterRegistry());
        }
    }

    @Override
    public void init() throws SQLException {
        if (this.databaseConfig.initializationFailTimeout() != null) {
            logger.debug("JdbcDataSource pool '{}' starting...", databaseConfig.poolName());
            var started = System.nanoTime();

            try (var connection = this.dataSource.getConnection()) {
                connection.isValid((int) this.databaseConfig.initializationFailTimeout().toMillis());
            } catch (SQLException e) {
                throw new IllegalStateException("JdbcDataSource pool '%s' failed to start due to: %s; check database availability, credentials, JDBC URL, and driver configuration".formatted(
                    databaseConfig.poolName(), e.getMessage()), e);
            }

            logger.info("JdbcDataSource pool '{}' started in {}", databaseConfig.poolName(), TimeUtils.tookForLogging(started));
        } else {
            logger.debug("JdbcDataSource pool '{}' initialization is skipped cause `initializationFailTimeout` is not specified...",
                databaseConfig.poolName());
        }
    }

    @Override
    public void release() {
        logger.debug("JdbcDataSource pool '{}' stopping...", databaseConfig.poolName());
        var started = System.nanoTime();

        this.dataSource.close();

        logger.info("JdbcDataSource pool '{}' stopped in {}", databaseConfig.poolName(), TimeUtils.tookForLogging(started));
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

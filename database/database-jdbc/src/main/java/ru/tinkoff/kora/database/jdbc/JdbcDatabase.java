package ru.tinkoff.kora.database.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.common.util.Configurer;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public class JdbcDatabase implements Lifecycle, Wrapped<DataSource>, JdbcConnectionFactory, ReadinessProbe {
    private static final Logger logger = LoggerFactory.getLogger(JdbcDatabase.class);

    private final JdbcDatabaseConfig databaseConfig;
    private final HikariDataSource dataSource;
    private final DataBaseTelemetry telemetry;
    private final ScopedValue<ConnectionContext> connectionContext = ScopedValue.newInstance();

    public JdbcDatabase(JdbcDatabaseConfig config, DataBaseTelemetryFactory telemetryFactory, @Nullable Configurer<HikariConfig> configurer) {
        this.databaseConfig = Objects.requireNonNull(config);
        var jdbcUrl = config.jdbcUrl();
        var jdbcDatabase = jdbcUrl.substring(5, jdbcUrl.indexOf(":", 5));
        this.telemetry = telemetryFactory.get(
            config.telemetry(),
            config.poolName(),
            jdbcDatabase
        );
        this.dataSource = new HikariDataSource(JdbcDatabaseConfig.toHikariConfig(this.databaseConfig, configurer));
        if (this.databaseConfig.telemetry().metrics().driverMetrics()) {
            this.dataSource.setMetricRegistry(this.telemetry.meterRegistry());
        }
    }

    @Override
    public void init() throws SQLException {
        if (this.databaseConfig.initializationFailTimeout() != null) {
            logger.debug("JdbcDatabase pool '{}' starting...", databaseConfig.poolName());
            var started = System.nanoTime();

            try (var connection = this.dataSource.getConnection()) {
                connection.isValid((int) this.databaseConfig.initializationFailTimeout().toMillis());
            } catch (SQLException e) {
                throw new RuntimeException("JdbcDatabase pool '%s' failed to start, due to: %s".formatted(
                    databaseConfig.poolName(), e.getMessage()), e);
            }

            logger.info("JdbcDatabase pool '{}' started in {}", databaseConfig.poolName(), TimeUtils.tookForLogging(started));
        } else {
            logger.debug("JdbcDatabase pool '{}' initialization is skipped cause `initializationFailTimeout` is not specified...",
                databaseConfig.poolName());
        }
    }

    @Override
    public void release() {
        logger.debug("JdbcDatabase pool '{}' stopping...", databaseConfig.poolName());
        var started = System.nanoTime();

        this.dataSource.close();

        logger.info("JdbcDatabase pool '{}' stopped in {}", databaseConfig.poolName(), TimeUtils.tookForLogging(started));
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
        if (this.connectionContext.isBound()) {
            return this.connectionContext.get().connection();
        }
        return null;
    }

    @Nullable
    @Override
    public ConnectionContext currentConnectionContext() {
        if (this.connectionContext.isBound()) {
            return this.connectionContext.get();
        }
        return null;
    }

    @Override
    public <T> T withConnection(JdbcHelper.SqlFunction1<Connection, T> callback) throws RuntimeSqlException {
        if (this.connectionContext.isBound()) {
            try {
                return callback.apply(this.connectionContext.get().connection());
            } catch (SQLException e) {
                throw new RuntimeSqlException(e);
            }
        }

        try (var connection = this.newConnection()) {
            return ScopedValue.where(this.connectionContext, new ConnectionContext(connection))
                .call(() -> callback.apply(connection));
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
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

package ru.tinkoff.kora.database.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
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

    public JdbcDatabase(JdbcDatabaseConfig config, DataBaseTelemetryFactory telemetryFactory, MeterRegistry meterRegistry) {
        this.databaseConfig = Objects.requireNonNull(config);
        var jdbcUrl = config.jdbcUrl();
        this.telemetry = telemetryFactory.get(
            config.telemetry(),
            config.poolName(),
            jdbcUrl.substring(4, jdbcUrl.indexOf(":", 5))
        );
        this.dataSource = new HikariDataSource(JdbcDatabaseConfig.toHikariConfig(this.databaseConfig));
        if (this.databaseConfig.telemetry().metrics().driverMetrics()) {
            this.dataSource.setMetricRegistry(meterRegistry);
        }
    }

    @Override
    public void init() throws SQLException {
        if (this.databaseConfig.initializationFailTimeout() != null) {
            logger.debug("JdbcDatabase pool '{}' starting...", databaseConfig.poolName());
            var started = System.nanoTime();

            try (var connection = this.dataSource.getConnection()) {
                connection.isValid((int) this.databaseConfig.initializationFailTimeout().toMillis());
            }
            logger.info("JdbcDatabase pool '{}' started in {}", databaseConfig.poolName(), TimeUtils.tookForLogging(started));
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
        var ctx = ConnectionContext.get();
        if (ctx == null) {
            return null;
        }
        return ctx.connection();
    }

    @Nullable
    @Override
    public ConnectionContext currentConnectionContext() {
        return ConnectionContext.get();
    }

    @Override
    public <T> T withConnection(JdbcHelper.SqlFunction1<Connection, T> callback) throws RuntimeSqlException {
        var currentConnectionCtx = ConnectionContext.get();
        if (currentConnectionCtx != null) {
            try {
                return callback.apply(currentConnectionCtx.connection());
            } catch (SQLException e) {
                throw new RuntimeSqlException(e);
            }
        }

        try (var connection = this.newConnection()) {
            var ctx = new ConnectionContext(connection);
            return ConnectionContext.with(ctx, () -> callback.apply(connection));
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

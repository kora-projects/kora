package ru.tinkoff.kora.database.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.readiness.ReadinessProbe;
import ru.tinkoff.kora.common.readiness.ReadinessProbeFailure;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetry;
import ru.tinkoff.kora.database.common.telemetry.DataBaseTelemetryFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.Executor;

public class JdbcDatabase implements Lifecycle, Wrapped<DataSource>, JdbcConnectionFactory, ReadinessProbe {
    private static final Logger logger = LoggerFactory.getLogger(JdbcDatabase.class);

    private final JdbcDatabaseConfig databaseConfig;
    private final HikariDataSource dataSource;
    private final DataBaseTelemetry telemetry;
    @Nullable
    final Executor executor;
    private final Context.Key<ConnectionContext> KEY = new Context.Key<>() {
        @Override
        protected ConnectionContext copy(ConnectionContext object) {
            return null;
        }
    };

    public JdbcDatabase(JdbcDatabaseConfig config, DataBaseTelemetryFactory telemetryFactory) {
        this(config, telemetryFactory, null);
    }

    public JdbcDatabase(JdbcDatabaseConfig databaseConfig, DataBaseTelemetry telemetry) {
        this(databaseConfig, telemetry, null);
    }

    public JdbcDatabase(JdbcDatabaseConfig config, DataBaseTelemetryFactory telemetryFactory, @Nullable Executor executor) {
        this(config, getTelemetry(config, telemetryFactory), executor);
    }

    public JdbcDatabase(JdbcDatabaseConfig databaseConfig, DataBaseTelemetry telemetry, @Nullable Executor executor) {
        this.databaseConfig = Objects.requireNonNull(databaseConfig);
        this.telemetry = Objects.requireNonNull(telemetry);
        this.dataSource = new HikariDataSource(JdbcDatabaseConfig.toHikariConfig(this.databaseConfig));
        if (telemetry.getMetricRegistry() != null) {
            this.dataSource.setMetricRegistry(telemetry.getMetricRegistry());
        }
        this.executor = executor;
    }

    private static DataBaseTelemetry getTelemetry(JdbcDatabaseConfig config, DataBaseTelemetryFactory factory) {
        var jdbcUrl = config.jdbcUrl();
        var telemetry = factory.get(
            config.telemetry(),
            config.poolName(),
            "jdbc",
            jdbcUrl.substring(4, jdbcUrl.indexOf(":", 5)),
            config.username()
        );
        return Objects.requireNonNullElse(telemetry, DataBaseTelemetryFactory.EMPTY);
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
        var ctx = Context.current().get(KEY);
        if (ctx == null) {
            return null;
        }
        return ctx.connection();
    }

    @Nullable
    @Override
    public ConnectionContext currentConnectionContext() {
        return Context.current().get(KEY);
    }

    @Override
    public <T> T withConnection(JdbcHelper.SqlFunction1<Connection, T> callback) throws RuntimeSqlException {
        var ctx = Context.current();

        var currentConnectionCtx = ctx.get(KEY);
        if (currentConnectionCtx != null) {
            try {
                return callback.apply(currentConnectionCtx.connection());
            } catch (SQLException e) {
                throw new RuntimeSqlException(e);
            }
        }

        try (var connection = ctx.set(KEY, new ConnectionContext(this.newConnection())).connection()) {
            return callback.apply(connection);
        } catch (SQLException e) {
            throw new RuntimeSqlException(e);
        } finally {
            ctx.remove(KEY);
        }
    }

    ConnectionContext setContext(Context context, ConnectionContext connectionContext) {
        return context.set(KEY, connectionContext);
    }

    void clearContext(Context context) {
        context.remove(KEY);
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

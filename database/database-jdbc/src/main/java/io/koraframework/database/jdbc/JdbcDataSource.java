package io.koraframework.database.jdbc;

import com.zaxxer.hikari.*;
import io.koraframework.application.graph.*;
import io.koraframework.common.*;
import io.koraframework.common.context.*;
import io.koraframework.common.readiness.*;
import io.koraframework.common.util.*;
import io.koraframework.database.common.telemetry.*;
import io.koraframework.database.jdbc.exception.*;
import org.jspecify.annotations.*;
import org.slf4j.*;

import javax.sql.*;
import java.sql.*;
import java.util.*;
import java.util.function.*;

public class JdbcDataSource implements Lifecycle, Wrapped<DataSource>, JdbcExecutor, ReadinessProbe {

    private static final Logger logger = LoggerFactory.getLogger(JdbcDataSource.class);

    public static final KoraContext.Key<ConnectionContext> CONNECTION_CONTEXT_KEY = KoraContext.Key.create();

    private final JdbcDatabaseConfig databaseConfig;
    private final HikariDataSource dataSource;
    private final DatabaseTelemetry telemetry;

    public JdbcDataSource(JdbcDatabaseConfig config, DatabaseTelemetryFactory telemetryFactory, @Nullable Configurer<HikariConfig> configurer) {
        this.databaseConfig = Objects.requireNonNull(config);
        var jdbcUrl = config.jdbcUrl();
        var jdbcDatabase = jdbcUrl.substring(5, jdbcUrl.indexOf(":", 5));
        this.telemetry = telemetryFactory.get(
            config.telemetry(),
            config.poolName(),
            jdbcDatabase
        );

        var hikariConfig = JdbcDatabaseConfig.toHikariConfig(this.databaseConfig, configurer);

        var threadFactory = Thread.ofVirtual().name("hikari-vt-", 1).factory();
        hikariConfig.setThreadFactory(threadFactory);

        if (this.databaseConfig.telemetry().metrics().driverMetrics()) {
            hikariConfig.setMetricRegistry(this.telemetry.meterRegistry());
        }
        this.dataSource = new HikariDataSource(hikariConfig);
    }

    @Override
    public void init() throws SQLException {
        logger.info("JdbcDataSource pool '{}' is hot and ready.", databaseConfig.poolName());
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
    @Override
    public Connection currentConnection() {
        KoraContext current = KoraContext.current();
        return current != null ? currentConnection(current) : null;
    }

    @Nullable
    @Override
    public Connection currentConnection(KoraContext ctx) {
        ConnectionContext connectionContext = ctx.get(CONNECTION_CONTEXT_KEY);
        return connectionContext != null ? connectionContext.connection() : null;
    }

    @Nullable
    public Connection connectionCurrent() {
        return currentConnection();
    }

    @Nullable
    @Override
    public ConnectionContext currentContext() {
        KoraContext current = KoraContext.current();
        return current != null ? current.get(CONNECTION_CONTEXT_KEY) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T withContext(SqlFunction<ConnectionContext, T> callback) throws UncheckedSqlException {
        KoraContext current = KoraContext.current();
        if (current != null) {
            ConnectionContext existingContext = current.get(CONNECTION_CONTEXT_KEY);
            if (existingContext != null) {
                try {
                    return callback.apply(existingContext);
                } catch (SQLException e) {
                    throw new UncheckedSqlException(e);
                }
            }
        }

        try (var connection = this.acquireConnection()) {
            var context = new ConnectionContext(connection);
            if (current != null) {
                current.with(CONNECTION_CONTEXT_KEY, context);
                try {
                    return callback.apply(context);
                } finally {
                    current.with(CONNECTION_CONTEXT_KEY, null);
                }
            }
            KoraContext updatedContext = KoraContext.empty().with(CONNECTION_CONTEXT_KEY, context);
            return (T) KoraContext.runWith(updatedContext, new KoraContextCallAction(callback, context));
        } catch (SQLException e) {
            throw new UncheckedSqlException(e);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    @SuppressWarnings("rawtypes")
    private static final class KoraContextCallAction implements Supplier<Object> {
        private final SqlFunction callback;
        private final ConnectionContext context;

        private KoraContextCallAction(SqlFunction callback, ConnectionContext context) {
            this.callback = callback;
            this.context = context;
        }

        @Override
        public Object get() {
            try {
                return callback.apply(context);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

package io.koraframework.database.jdbc.benchmark;

import io.koraframework.database.common.telemetry.DatabaseTelemetryConfig;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseTelemetryFactory;
import io.koraframework.database.jdbc.JdbcDataSource;
import io.koraframework.database.jdbc.agroal.AgroalJdbcDatabaseConfig;
import io.koraframework.database.jdbc.agroal.AgroalJdbcDatabaseFactoryModule;
import io.koraframework.database.jdbc.hikari.HikariJdbcDatabaseConfig;
import io.koraframework.database.jdbc.hikari.HikariJdbcDatabaseFactoryModule;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 10)
public class JdbcPoolBenchmark {

    @Param({"hikari", "agroal"})
    public String pool;

    @Param({"10"})
    public int poolSize;

    private JdbcDataSource dataSource;
    private String insertSql;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        var postgres = DatabaseParams.resolve();
        this.insertSql = "INSERT INTO bench_jdbc_pool(value) VALUES (?)";

        this.dataSource = switch (this.pool) {
            case "hikari" -> new HikariJdbcDatabaseFactoryModule("benchmark")
                .jdbcDataSource(new HikariConfig(postgres, this.poolSize), NoopDatabaseTelemetryFactory.INSTANCE, null);
            case "agroal" -> new AgroalJdbcDatabaseFactoryModule("benchmark")
                .jdbcDataSource(new AgroalConfig(postgres, this.poolSize), NoopDatabaseTelemetryFactory.INSTANCE, null);
            default -> throw new IllegalArgumentException("Unknown pool: " + this.pool);
        };
        this.dataSource.init();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (this.dataSource != null) {
            this.dataSource.release();
        }
    }

    @Benchmark
    public void acquireRelease() throws SQLException {
        try (var connection = this.dataSource.value().getConnection()) {
            connection.isValid(1);
        }
    }

    @Benchmark
    public int selectOne() throws SQLException {
        try (var connection = this.dataSource.value().getConnection();
             var statement = connection.createStatement();
             var rs = statement.executeQuery("SELECT 1")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Benchmark
    public String preparedSelectById() throws SQLException {
        try (var connection = this.dataSource.value().getConnection();
             var statement = connection.prepareStatement("SELECT value FROM bench_jdbc_pool WHERE id = ?")) {
            statement.setLong(1, 500);
            try (var rs = statement.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    @Benchmark
    public void transactionRollback() throws SQLException {
        try (var connection = this.dataSource.value().getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(this.insertSql)) {
                statement.setString(1, "rollback");
                statement.executeUpdate();
            } finally {
                connection.rollback();
                connection.setAutoCommit(true);
            }
        }
    }

    @Benchmark
    public void holdConnection5ms() throws SQLException {
        try (var ignored = this.dataSource.value().getConnection()) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        }
    }

    private record HikariConfig(DatabaseParams postgres, int poolSize) implements HikariJdbcDatabaseConfig {
        @Override
        public Duration connectionTimeout() {
            return Duration.ofSeconds(10);
        }

        @Override
        public Duration idleTimeout() {
            return Duration.ofMinutes(10);
        }

        @Override
        public Duration maxLifetime() {
            return Duration.ofMinutes(15);
        }

        @Override
        public Duration leakDetectionThreshold() {
            return Duration.ZERO;
        }

        @Override
        public int maxPoolSize() {
            return this.poolSize;
        }

        @Override
        public int minIdle() {
            return 0;
        }

        @Override
        public Properties dsProperties() {
            return new Properties();
        }

        @Override
        public String username() {
            return this.postgres.username();
        }

        @Override
        public String password() {
            return this.postgres.password();
        }

        @Override
        public String jdbcUrl() {
            return this.postgres.jdbcUrl();
        }

        @Override
        public String poolName() {
            return "hikari-benchmark";
        }

        @Override
        public String schema() {
            return null;
        }

        @Override
        public Duration validationTimeout() {
            return Duration.ofSeconds(5);
        }

        @Override
        public Duration initializationFailTimeout() {
            return Duration.ofSeconds(10);
        }

        @Override
        public DatabaseTelemetryConfig telemetry() {
            return TelemetryConfig.INSTANCE;
        }
    }

    private record AgroalConfig(DatabaseParams postgres, int poolSize) implements AgroalJdbcDatabaseConfig {
        @Override
        public Duration acquisitionTimeout() {
            return Duration.ofSeconds(10);
        }

        @Override
        public Duration idleValidationTimeout() {
            return Duration.ofMinutes(10);
        }

        @Override
        public Duration maxLifetime() {
            return Duration.ofMinutes(15);
        }

        @Override
        public Duration leakTimeout() {
            return Duration.ZERO;
        }

        @Override
        public int maxPoolSize() {
            return this.poolSize;
        }

        @Override
        public int minPoolSize() {
            return 0;
        }

        @Override
        public int initialPoolSize() {
            return 0;
        }

        @Override
        public boolean trackJdbcResources() {
            return false;
        }

        @Override
        public boolean recoveryEnable() {
            return false;
        }

        @Override
        public Properties jdbcProperties() {
            return new Properties();
        }

        @Override
        public String username() {
            return this.postgres.username();
        }

        @Override
        public String password() {
            return this.postgres.password();
        }

        @Override
        public String jdbcUrl() {
            return this.postgres.jdbcUrl();
        }

        @Override
        public String poolName() {
            return "agroal-benchmark";
        }

        @Override
        public String schema() {
            return null;
        }

        @Override
        public Duration validationTimeout() {
            return Duration.ofSeconds(5);
        }

        @Override
        public Duration initializationFailTimeout() {
            return Duration.ofSeconds(10);
        }

        @Override
        public DatabaseTelemetryConfig telemetry() {
            return TelemetryConfig.INSTANCE;
        }
    }

    private enum TelemetryConfig implements DatabaseTelemetryConfig {
        INSTANCE;

        @Override
        public DatabaseLoggingConfig logging() {
            return new DatabaseLoggingConfig() {
                @Override
                public boolean enabled() {
                    return false;
                }
            };
        }

        @Override
        public DatabaseMetricsConfig metrics() {
            return new DatabaseMetricsConfig() {
                @Override
                public boolean enabled() {
                    return false;
                }

                @Override
                public boolean driverMetrics() {
                    return false;
                }

                @Override
                public Duration[] slo() {
                    return new Duration[0];
                }

                @Override
                public Map<String, String> tags() {
                    return Map.of();
                }
            };
        }

        @Override
        public DatabaseTracingConfig tracing() {
            return new DatabaseTracingConfig() {
                @Override
                public boolean enabled() {
                    return false;
                }

                @Override
                public Map<String, String> attributes() {
                    return Map.of();
                }
            };
        }
    }

    private record DatabaseParams(String jdbcUrl, String username, String password) {
        private static DatabaseParams resolve() {
            var jdbcUrl = System.getProperty("benchmark.postgres.jdbcUrl");
            var username = System.getProperty("benchmark.postgres.username");
            var password = System.getProperty("benchmark.postgres.password");
            if (jdbcUrl != null && username != null && password != null) {
                return new DatabaseParams(jdbcUrl, username, password);
            }
            var container = SharedPostgres.container();
            return new DatabaseParams(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        }
    }

    private static final class SharedPostgres {
        private static final PostgreSQLContainer<?> CONTAINER = start();

        private SharedPostgres() {}

        static PostgreSQLContainer<?> container() {
            return CONTAINER;
        }

        private static PostgreSQLContainer<?> start() {
            var container = new PostgreSQLContainer<>("postgres:17.6-alpine");
            container.start();
            Runtime.getRuntime().addShutdownHook(new Thread(container::stop, "jdbc-pool-benchmark-postgres-stop"));
            initSchema(container);
            return container;
        }

        private static void initSchema(PostgreSQLContainer<?> container) {
            try (var connection = container.createConnection("?");
                 var statement = connection.createStatement()) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS bench_jdbc_pool(
                        id BIGSERIAL PRIMARY KEY,
                        value VARCHAR NOT NULL
                    )
                    """);
                try (var rs = statement.executeQuery("SELECT count(*) FROM bench_jdbc_pool")) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        return;
                    }
                }
                for (int i = 0; i < 1000; i++) {
                    statement.addBatch("INSERT INTO bench_jdbc_pool(value) VALUES ('value_%d')".formatted(i));
                }
                statement.executeBatch();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

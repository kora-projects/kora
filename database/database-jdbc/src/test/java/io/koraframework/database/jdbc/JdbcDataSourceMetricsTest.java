package io.koraframework.database.jdbc;

import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper;
import io.koraframework.database.common.telemetry.impl.DefaultDatabaseTelemetryFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseLoggerFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseMetricsFactory;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith({PostgresTestContainer.class})
class JdbcDataSourceMetricsTest {

    private static final String POOL = "metricsPool";

    @Test
    void driverMetricsSurviveAPoolReplacedByRefresh(PostgresParams params) throws Exception {
        var registry = new SimpleMeterRegistry();

        var previous = dataSource(params, registry);
        previous.init();
        previous.graphRefreshed();
        assertThat(registry.find("hikaricp.connections").tag("pool", POOL).gauges()).isNotEmpty();

        // a refresh builds the replacement while the previous pool is still running,
        // and releases the previous one only after the new subgraph is ready
        var replacement = dataSource(params, registry);
        replacement.init();
        previous.release();
        replacement.graphRefreshed();

        try {
            assertThat(registry.find("hikaricp.connections").tag("pool", POOL).gauges()).isNotEmpty();
        } finally {
            replacement.release();
        }
    }

    private static JdbcDataSource dataSource(PostgresParams params, MeterRegistry registry) throws SQLException {
        var config = new $JdbcDatabaseConfig_ConfigValueMapper.JdbcDatabaseConfig_Impl(
            params.user(),
            params.password(),
            params.jdbcUrl(),
            POOL,
            null,
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            1,
            0,
            Duration.ofMillis(1000L),
            false,
            new Properties(),
            new $DatabaseTelemetryConfig_ConfigValueMapper.DatabaseTelemetryConfig_Impl(
                new $DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueMapper.DatabaseLoggingConfig_Impl(false),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueMapper.DatabaseMetricsConfig_Impl(true, true, new Duration[0], Map.of()),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueMapper.DatabaseTracingConfig_Impl(false, Map.of())
            )
        );
        return new JdbcDataSource(
            config,
            new DefaultDatabaseTelemetryFactory(TracerProvider.noop().get(""), registry, NoopDatabaseLoggerFactory.INSTANCE, NoopDatabaseMetricsFactory.INSTANCE),
            null
        );
    }
}

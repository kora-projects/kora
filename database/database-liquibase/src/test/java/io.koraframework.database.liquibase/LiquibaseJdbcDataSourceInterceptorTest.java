package io.koraframework.database.liquibase;

import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_ConfigValueExtractor;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueExtractor;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueExtractor;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueExtractor;
import io.koraframework.database.common.telemetry.impl.DefaultDatabaseTelemetryFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseLoggerFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseMetricsFactory;
import io.koraframework.database.jdbc.$JdbcDatabaseConfig_ConfigValueExtractor;
import io.koraframework.database.jdbc.JdbcDataSource;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

@ExtendWith({PostgresTestContainer.class})
public class LiquibaseJdbcDataSourceInterceptorTest {

    @Test
    public void testLiquibaseInterceptor(PostgresParams params) throws SQLException {
        var config = new $JdbcDatabaseConfig_ConfigValueExtractor.JdbcDatabaseConfig_Impl(
            params.user(),
            params.password(),
            params.jdbcUrl(),
            "testPool",
            null,
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            Duration.ofMillis(1000L),
            1, // Liquibase usually uses one connection
            0,
            Duration.ofMillis(1000L),
            false,
            new Properties(),
            new $DatabaseTelemetryConfig_ConfigValueExtractor.DatabaseTelemetryConfig_Impl(
                new $DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueExtractor.DatabaseLoggingConfig_Impl(true),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueExtractor.DatabaseMetricsConfig_Impl(true, true, new Duration[0], Map.of()),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueExtractor.DatabaseTracingConfig_Impl(true, Map.of())
            )
        );

        var database = new JdbcDataSource(config, new DefaultDatabaseTelemetryFactory(TracerProvider.noop().get(""), new CompositeMeterRegistry(), NoopDatabaseLoggerFactory.INSTANCE, NoopDatabaseMetricsFactory.INSTANCE), null);
        database.init();
        try {
            var interceptor = new LiquibaseJdbcDatabaseInterceptor(new LiquibaseConfig() {});
            Assertions.assertSame(database, interceptor.afterInit(database), "LiquibaseJdbcDatabaseInterceptor should return same reference on init");

            database.inTx(ctx -> {
                var resultSet = ctx.connection()
                    .createStatement()
                    .executeQuery("SELECT * FROM test_migrated_table WHERE id = 100");

                Assertions.assertTrue(resultSet.next(), "test_migrated_table should contain row with id = 100");
                Assertions.assertAll(
                    () -> Assertions.assertEquals(100, resultSet.getLong("id"), "id should be equal to 100"),
                    () -> Assertions.assertEquals("foo", resultSet.getString("name"), "name should be equal to 'foo'")
                );
            });

            Assertions.assertSame(database, interceptor.beforeRelease(database), "LiquibaseJdbcDatabaseInterceptor should return same reference on release");
        } finally {
            database.release();
        }
    }
}

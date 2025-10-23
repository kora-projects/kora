package ru.tinkoff.kora.database.liquibase;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.tinkoff.kora.database.common.telemetry.*;
import ru.tinkoff.kora.database.jdbc.$JdbcDatabaseConfig_ConfigValueExtractor;
import ru.tinkoff.kora.database.jdbc.JdbcDatabase;
import ru.tinkoff.kora.test.postgres.PostgresParams;
import ru.tinkoff.kora.test.postgres.PostgresTestContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

@ExtendWith({PostgresTestContainer.class})
public class LiquibaseJdbcDatabaseInterceptorTest {

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
                new $DatabaseTelemetryConfig_DatabaseLogConfig_ConfigValueExtractor.DatabaseLogConfig_Impl(true),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueExtractor.DatabaseTracingConfig_Impl(true, Map.of()),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueExtractor.DatabaseMetricsConfig_Impl(true, true, new Duration[0], Map.of())
            )
        );

        var dataBase = new JdbcDatabase(config, new DefaultDataBaseTelemetryFactory(TracerProvider.noop().get(""), new CompositeMeterRegistry()), new CompositeMeterRegistry());
        dataBase.init();
        try {
            var interceptor = new LiquibaseJdbcDatabaseInterceptor(new LiquibaseConfig() {});
            Assertions.assertSame(dataBase, interceptor.init(dataBase), "LiquibaseJdbcDatabaseInterceptor should return same reference on init");

            dataBase.inTx((Connection connection) -> {
                var resultSet = connection
                    .createStatement()
                    .executeQuery("SELECT * FROM test_migrated_table WHERE id = 100");

                Assertions.assertTrue(resultSet.next(), "test_migrated_table should contain row with id = 100");
                Assertions.assertAll(
                    () -> Assertions.assertEquals(100, resultSet.getLong("id"), "id should be equal to 100"),
                    () -> Assertions.assertEquals("foo", resultSet.getString("name"), "name should be equal to 'foo'")
                );
            });

            Assertions.assertSame(dataBase, interceptor.release(dataBase), "LiquibaseJdbcDatabaseInterceptor should return same reference on release");
        } finally {
            dataBase.release();
        }
    }
}

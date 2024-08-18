package ru.tinkoff.kora.database.liquibase;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory;
import ru.tinkoff.kora.database.jdbc.$JdbcDatabaseConfig_ConfigValueExtractor;
import ru.tinkoff.kora.database.jdbc.JdbcDatabase;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_LogConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_MetricsConfig_ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.$TelemetryConfig_TracingConfig_ConfigValueExtractor;
import ru.tinkoff.kora.test.postgres.PostgresParams;
import ru.tinkoff.kora.test.postgres.PostgresTestContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
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
            new $TelemetryConfig_ConfigValueExtractor.TelemetryConfig_Impl(
                new $TelemetryConfig_LogConfig_ConfigValueExtractor.LogConfig_Impl(true),
                new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(true),
                new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(null, null)
            )
        );

        var dataBase = new JdbcDatabase(config, new DefaultDataBaseTelemetryFactory(null, null, null));
        dataBase.init();
        try {
            var interceptor = new LiquibaseJdbcDatabaseInterceptor();
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

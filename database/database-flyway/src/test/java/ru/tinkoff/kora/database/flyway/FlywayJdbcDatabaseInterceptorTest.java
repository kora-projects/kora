package ru.tinkoff.kora.database.flyway;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory;
import ru.tinkoff.kora.database.jdbc.JdbcDatabase;
import ru.tinkoff.kora.database.jdbc.JdbcDatabaseConfig;
import ru.tinkoff.kora.test.postgres.PostgresParams;
import ru.tinkoff.kora.test.postgres.PostgresTestContainer;

import java.sql.Connection;
import java.time.Duration;
import java.util.Properties;

@ExtendWith({PostgresTestContainer.class})
public class FlywayJdbcDatabaseInterceptorTest {

    @Test
    public void testFlywayInterceptor(PostgresParams params) {
        var config = new JdbcDatabaseConfig(
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
            2, // flyway uses two connections for migration and schema management
            0,
            new Properties()
        );
        var dataBase = new JdbcDatabase(config, new DefaultDataBaseTelemetryFactory(null, null, null));
        dataBase.init().block();
        try {

            var interceptor = new FlywayJdbcDatabaseInterceptor();
            Assertions.assertSame(dataBase, interceptor.init(dataBase).block(), "FlywayJdbcDatabaseInterceptor should return same reference on init");

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

            Assertions.assertSame(dataBase, interceptor.release(dataBase).block(), "FlywayJdbcDatabaseInterceptor should return same reference on release");
        } finally {
            dataBase.release().block();
        }

    }
}

package ru.tinkoff.kora.database.jdbc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.database.common.telemetry.DefaultDataBaseTelemetryFactory;
import ru.tinkoff.kora.test.postgres.PostgresParams;
import ru.tinkoff.kora.test.postgres.PostgresTestContainer;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

@ExtendWith({PostgresTestContainer.class})
class JdbcDatabaseTest {
    static {
        if (LoggerFactory.getLogger("ROOT") instanceof Logger log) {
            log.setLevel(Level.INFO);
        }
        if (LoggerFactory.getLogger("ru.tinkoff.kora") instanceof Logger log) {
            log.setLevel(Level.DEBUG);
        }
    }

    private static void withDb(PostgresParams params, Consumer<JdbcDatabase> consumer) {
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
            1,
            0,
            new Properties()
        );
        var db = new JdbcDatabase(config, new DefaultDataBaseTelemetryFactory(null, null, null));
        db.init().block();
        try {
            consumer.accept(db);
        } finally {
            db.release().block();
        }
    }

    @Test
    void testQuery(PostgresParams params) {
        var tableName = PostgresTestContainer.randomName("test_table");
        params.execute("""
            CREATE TABLE %s(id BIGSERIAL, value VARCHAR);
            INSERT INTO %s(value) VALUES ('test1');
            INSERT INTO %s(value) VALUES ('test2');
            """.formatted(tableName, tableName, tableName));

        var id = "SELECT * FROM %s WHERE value = :value".formatted(tableName);
        var sql = "SELECT * FROM %s WHERE value = ?".formatted(tableName);
        record Entity(long id, String value) {}


        withDb(params, db -> {
            var result = db.withConnection(() -> {
                var r = new ArrayList<Entity>();
                try (var stmt = db.currentConnection().prepareStatement(sql);) {
                    stmt.setString(1, "test1");
                    var rs = stmt.executeQuery();
                    while (rs.next()) {
                        r.add(new Entity(rs.getInt(1), rs.getString(2)));
                    }
                }
                return r;
            });
            Assertions.assertThat(result).containsExactly(new Entity(1, "test1"));
        });
    }

    @Test
    void testTransaction(PostgresParams params) {
        var tableName = "test_table_" + PostgresTestContainer.randomName("test_table");
        params.execute("CREATE TABLE %s(id BIGSERIAL, value VARCHAR);".formatted(tableName));
        var id = "INSERT INTO %s(value) VALUES ('test1');".formatted(tableName);
        var sql = "INSERT INTO %s(value) VALUES ('test1');".formatted(tableName);
        PostgresParams.ResultSetMapper<List<String>, RuntimeException> extractor = rs -> {
            var result = new ArrayList<String>();
            try {
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
            } catch (SQLException sqlException) {
                throw new RuntimeException(sqlException);
            }
            return result;
        };

        withDb(params, db -> {
            Assertions.assertThatThrownBy(() -> db.inTx((JdbcHelper.SqlRunnable) () -> {
                try (var stmt = db.currentConnection().prepareStatement(sql)) {
                    stmt.execute();
                }
                throw new RuntimeException();
            }));


            var values = params.query("SELECT value FROM %s".formatted(tableName), extractor);
            Assertions.assertThat(values).hasSize(0);

            db.inTx(() -> {
                try (var stmt = db.currentConnection().prepareStatement(sql)) {
                    stmt.execute();
                }
            });

            values = params.query("SELECT value FROM %s".formatted(tableName), extractor);
            Assertions.assertThat(values).hasSize(1);
        });
    }
}

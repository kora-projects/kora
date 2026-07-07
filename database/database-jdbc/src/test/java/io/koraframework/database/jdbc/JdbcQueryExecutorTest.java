package io.koraframework.database.jdbc;

import io.koraframework.database.common.UpdateCount;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_ConfigValueExtractor;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueExtractor;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueExtractor;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueExtractor;
import io.koraframework.database.common.telemetry.impl.DefaultDatabaseTelemetryFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseLoggerFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseMetricsFactory;
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.JDBCType;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

@ExtendWith({PostgresTestContainer.class})
class JdbcQueryExecutorTest {

    private static void withDb(PostgresParams params, Consumer<JdbcDataSource> consumer) throws SQLException {
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
            Duration.ofMillis(1000L),
            false,
            new Properties(),
            new $DatabaseTelemetryConfig_ConfigValueExtractor.DatabaseTelemetryConfig_Impl(
                new $DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueExtractor.DatabaseLoggingConfig_Impl(true),
                new $DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueExtractor.DatabaseMetricsConfig_Impl(true, true, new Duration[0], Map.of()),
                new $DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueExtractor.DatabaseTracingConfig_Impl(true, Map.of())
            )
        );
        var db = new JdbcDataSource(config, new DefaultDatabaseTelemetryFactory(TracerProvider.noop().get(""), new CompositeMeterRegistry(), NoopDatabaseLoggerFactory.INSTANCE, NoopDatabaseMetricsFactory.INSTANCE), null);
        db.init();
        try {
            consumer.accept(db);
        } finally {
            db.release();
        }
    }

    @Test
    void testJdbcQuery(PostgresParams params) throws SQLException {
        var tableName = PostgresTestContainer.randomName("test_table");
        params.execute("""
            CREATE TABLE %s(id BIGSERIAL, value VARCHAR, status VARCHAR);
            INSERT INTO %s(value, status) VALUES ('test1', 'active');
            INSERT INTO %s(value, status) VALUES ('test2', 'archived');
            INSERT INTO %s(value, status) VALUES ('test3', 'active');
            """.formatted(tableName, tableName, tableName, tableName));

        record Entity(long id, String value) {}

        withDb(params, db -> {
            var status = "active";
            var query = JdbcQuery.named()
                .sql("SELECT id, value FROM %s WHERE 1 = 1".formatted(tableName))
                .sqlIf(" AND status = :status", status != null)
                .bindIf("status", status, status != null)
                .sql(" AND value IN (:values)")
                .bindIn("values", Arrays.asList("test1", "test3"))
                .sql(" ORDER BY id")
                .build();
            JdbcRowMapper<Entity> mapper = rs -> new Entity(rs.getLong("id"), rs.getString("value"));

            var result = db.queryList(query, mapper);

            Assertions.assertThat(result)
                .containsExactly(new Entity(1, "test1"), new Entity(3, "test3"));

            var one = db.queryOne(
                JdbcQuery.named()
                    .sql("SELECT id, value FROM %s WHERE value = :value".formatted(tableName))
                    .bind("value", "test1")
                    .build(),
                mapper
            );
            Assertions.assertThat(one).isEqualTo(new Entity(1, "test1"));

            var optional = db.queryOptional(
                JdbcQuery.named()
                    .sql("SELECT id, value FROM %s WHERE value = :value".formatted(tableName))
                    .bind("value", "missing")
                    .build(),
                mapper
            );
            Assertions.assertThat(optional).isEmpty();
        });
    }

    @Test
    void testJdbcQueryNewContracts(PostgresParams params) throws SQLException {
        var tableName = PostgresTestContainer.randomName("test_table");
        params.execute("CREATE TABLE %s(id BIGSERIAL, value VARCHAR);".formatted(tableName));

        withDb(params, db -> {
            var insert = JdbcQuery.named()
                .sql("INSERT INTO %s(value) VALUES (:value)".formatted(tableName))
                .bind("value", "test1", JDBCType.VARCHAR)
                .build();
            Assertions.assertThat(db.executeUpdate(insert)).isEqualTo(new UpdateCount(1));

            var generatedId = db.executeUpdate(
                JdbcQuery.named()
                    .sql("INSERT INTO %s(value) VALUES (:value)".formatted(tableName))
                    .opts(o -> o.returnGeneratedKeys("id"))
                    .bind("value", "test2")
                    .build(),
                rs -> {
                    Assertions.assertThat(rs.next()).isTrue();
                    return rs.getLong(1);
                }
            );
            Assertions.assertThat(generatedId).isEqualTo(2L);

            var result = db.queryList(
                JdbcQuery.template("SELECT value FROM %s WHERE id > ? ORDER BY id".formatted(tableName), 0),
                rs -> rs.getString("value")
            );
            Assertions.assertThat(result).containsExactly("test1", "test2");

            var namedBatch = JdbcQuery.named()
                .sql("INSERT INTO %s(value) VALUES (:value)".formatted(tableName))
                .batch()
                .bind(Map.of("value", "test3"))
                .bind(row -> row.bind("value", "test4"))
                .build();
            Assertions.assertThat(db.executeUpdateBatch(namedBatch)).isEqualTo(new UpdateCount(2));

            var templateBatch = JdbcQuery.template()
                .sql("INSERT INTO %s(value) VALUES (?)".formatted(tableName))
                .batch()
                .bind("test5")
                .bindAll(List.of("test6"), JdbcQuery.TemplateRowBinder::bind)
                .build();
            Assertions.assertThat(db.executeUpdateBatch(templateBatch)).isEqualTo(new UpdateCount(2));
        });
    }
}

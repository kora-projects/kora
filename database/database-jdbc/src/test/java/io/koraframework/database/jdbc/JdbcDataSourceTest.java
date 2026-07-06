package io.koraframework.database.jdbc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_ConfigValueExtractor;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseLoggingConfig_ConfigValueExtractor;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseMetricsConfig_ConfigValueExtractor;
import io.koraframework.database.common.telemetry.$DatabaseTelemetryConfig_DatabaseTracingConfig_ConfigValueExtractor;
import io.koraframework.database.common.UpdateCount;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseLoggerFactory;
import io.koraframework.database.common.telemetry.impl.NoopDatabaseMetricsFactory;
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import io.koraframework.database.common.telemetry.impl.DefaultDatabaseTelemetryFactory;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;

import java.lang.reflect.Proxy;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

@ExtendWith({PostgresTestContainer.class})
class JdbcDataSourceTest {
    static {
        if (LoggerFactory.getLogger("ROOT") instanceof Logger log) {
            log.setLevel(Level.INFO);
        }
        if (LoggerFactory.getLogger("io.koraframework") instanceof Logger log) {
            log.setLevel(Level.DEBUG);
        }
    }

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
    void testQuery(PostgresParams params) throws SQLException {
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
                try (var stmt = db.connectionCurrent().prepareStatement(sql);) {
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
    void testJdbcQueryBuilder() {
        var status = "active";
        String name = null;
        var query = JdbcQuery.named()
            .sql("SELECT * FROM users WHERE 1 = 1")
            .sqlIf(" AND status = :status", status != null)
            .bindIf("status", status, status != null)
            .sqlIf(" AND name = :name", name != null)
            .bindIf("name", name, name != null)
            .sql(" AND id IN (:ids)")
            .bind("ids", List.of(1L, 2L, 3L))
            .sqlIf(" ORDER BY id", true)
            .build();

        Assertions.assertThat(query.sourceSql())
            .isEqualTo("SELECT * FROM users WHERE 1 = 1 AND status = :status AND id IN (:ids) ORDER BY id");
        Assertions.assertThat(query.sql())
            .isEqualTo("SELECT * FROM users WHERE 1 = 1 AND status = ? AND id IN (?, ?, ?) ORDER BY id");
        Assertions.assertThat(query.parameterValues())
            .containsExactly("active", 1L, 2L, 3L);
    }

    @Test
    void testJdbcQueryTypedParameters() throws SQLException {
        var calls = new ArrayList<String>();
        var statement = (PreparedStatement) Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            (proxy, method, args) -> {
                if (method.getName().equals("setNull")) {
                    calls.add("setNull:" + args[0] + ":" + args[1]);
                } else if (method.getName().equals("setString")) {
                    calls.add("setString:" + args[0] + ":" + args[1]);
                } else if (method.getName().equals("setObject")) {
                    calls.add("setObject:" + args[0] + ":" + args[1] + ":" + args[2]);
                } else if (method.getName().equals("close")) {
                    calls.add("close");
                }
                return null;
            }
        );
        var connection = (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> method.getName().equals("prepareStatement")
                ? statement
                : null
        );

        var query = JdbcQuery.named()
            .sql("SELECT :nullable_value, :mapped_value, :ids")
            .bind("nullable_value", null, Types.VARCHAR)
            .bind("mapped_value", "test", (stmt, index, value) -> stmt.setString(index, "mapped-" + value))
            .bind("ids", List.of(1L, 2L), Types.BIGINT)
            .build();

        try (var ignored = query.prepare(connection)) {
            Assertions.assertThat(calls)
                .containsExactly(
                    "setNull:1:" + Types.VARCHAR,
                    "setString:2:mapped-test",
                    "setObject:3:1:" + Types.BIGINT,
                    "setObject:4:2:" + Types.BIGINT
                );
        }
    }

    @Test
    void testJdbcTemplateQueryBuilder() throws SQLException {
        var calls = new ArrayList<String>();
        var statement = (PreparedStatement) Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            (proxy, method, args) -> {
                if (method.getName().equals("setObject")) {
                    calls.add(args.length == 2
                        ? "setObject:" + args[0] + ":" + args[1]
                        : "setObject:" + args[0] + ":" + args[1] + ":" + args[2]);
                }
                return null;
            }
        );
        var connection = (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> method.getName().equals("prepareStatement")
                ? statement
                : null
        );

        var query = JdbcQuery.template()
            .sql("SELECT * FROM %s WHERE name = ?", "users")
            .sqlIf(" AND age > ?", true)
            .sqlIf(" AND deleted = ?", false)
            .bindAll("alice")
            .bind(42, JDBCType.INTEGER)
            .build();

        Assertions.assertThat(query.sourceSql()).isEqualTo("SELECT * FROM users WHERE name = ? AND age > ?");
        Assertions.assertThat(query.sql()).isEqualTo("SELECT * FROM users WHERE name = ? AND age > ?");
        Assertions.assertThat(query.parameterValues()).containsExactly("alice", 42);

        try (var ignored = query.prepare(connection)) {
            Assertions.assertThat(calls)
                .containsExactly(
                    "setObject:1:alice",
                    "setObject:2:42:" + Types.INTEGER
                );
        }
    }

    @Test
    void testJdbcQueryOptionsDefaults() {
        var generatedKeys = JdbcQuery.OptsBuilder.builder()
            .fetchSize(100)
            .maxRows(10)
            .queryTimeoutSeconds(5)
            .resultSetType(JdbcQueryOptions.ResultSetType.SCROLL_INSENSITIVE)
            .resultSetConcurrency(JdbcQueryOptions.ResultSetConcurrency.UPDATABLE)
            .resultSetHoldability(JdbcQueryOptions.ResultSetHoldability.CLOSE_CURSORS_AT_COMMIT)
            .returnGeneratedKeys()
            .build();

        Assertions.assertThat(generatedKeys.fetchSize()).isEqualTo(100);
        Assertions.assertThat(generatedKeys.maxRows()).isEqualTo(10);
        Assertions.assertThat(generatedKeys.queryTimeoutSeconds()).isEqualTo(5);
        Assertions.assertThat(generatedKeys.resultSetType()).isEqualTo(ResultSet.TYPE_SCROLL_INSENSITIVE);
        Assertions.assertThat(generatedKeys.resultSetConcurrency()).isEqualTo(ResultSet.CONCUR_UPDATABLE);
        Assertions.assertThat(generatedKeys.resultSetHoldability()).isEqualTo(ResultSet.CLOSE_CURSORS_AT_COMMIT);
        Assertions.assertThat(generatedKeys.autoGeneratedKeys()).isEqualTo(Statement.RETURN_GENERATED_KEYS);
        Assertions.assertThat(generatedKeys.generatedKeyColumns()).isEmpty();

        var generatedKeyColumns = JdbcQuery.OptsBuilder.builder()
            .generatedKeys(JdbcQueryOptions.GeneratedKeys.NO_GENERATED_KEYS)
            .returnGeneratedKeys("id", "version")
            .build();

        Assertions.assertThat(generatedKeyColumns.autoGeneratedKeys()).isNull();
        Assertions.assertThat(generatedKeyColumns.generatedKeyColumns()).containsExactly("id", "version");
    }

    @Test
    void testJdbcBatchBuildersDefaults() {
        var first = new LinkedHashMap<String, Object>();
        first.put("value", "test1");
        first.put("status", "active");
        var second = new LinkedHashMap<String, Object>();
        second.put("value", "test2");
        second.put("status", "archived");

        var namedBatch = JdbcQuery.named()
            .sql("INSERT INTO %s(value, status) VALUES (:value, :status)", "users")
            .batch()
            .bindAll(List.of(first, second), (row, values) -> row.bindAll(values))
            .build();

        Assertions.assertThat(namedBatch.sourceSql()).isEqualTo("INSERT INTO users(value, status) VALUES (:value, :status)");
        Assertions.assertThat(namedBatch.sql()).isEqualTo("INSERT INTO users(value, status) VALUES (?, ?)");
        Assertions.assertThat(namedBatch.size()).isEqualTo(2);
        Assertions.assertThat(namedBatch.parameters(0).stream().map(JdbcQuery.Parameter::value)).containsExactly("test1", "active");
        Assertions.assertThat(namedBatch.parameters(1).stream().map(JdbcQuery.Parameter::value)).containsExactly("test2", "archived");

        var templateBatch = JdbcQuery.template()
            .sql("INSERT INTO %s(value, status) VALUES (?, ?)", "users")
            .batch()
            .bindAll(List.of(List.of("test1", "active"), List.of("test2", "archived")), (row, values) -> row.bindAll(values.toArray()))
            .build();

        Assertions.assertThat(templateBatch.sourceSql()).isEqualTo("INSERT INTO users(value, status) VALUES (?, ?)");
        Assertions.assertThat(templateBatch.sql()).isEqualTo("INSERT INTO users(value, status) VALUES (?, ?)");
        Assertions.assertThat(templateBatch.size()).isEqualTo(2);
        Assertions.assertThat(templateBatch.parameters(0).stream().map(JdbcQuery.Parameter::value)).containsExactly("test1", "active");
        Assertions.assertThat(templateBatch.parameters(1).stream().map(JdbcQuery.Parameter::value)).containsExactly("test2", "archived");
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
                .sql("SELECT id, value FROM %s WHERE 1 = 1", tableName)
                .sqlIf(" AND status = :status", status != null)
                .bindIf("status", status, status != null)
                .sql(" AND value IN (:values)")
                .bind("values", Arrays.asList("test1", "test3"))
                .sql(" ORDER BY id")
                .build();
            JdbcRowMapper<Entity> mapper = rs -> new Entity(rs.getLong("id"), rs.getString("value"));

            var result = db.queryList(query, mapper);

            Assertions.assertThat(result)
                .containsExactly(new Entity(1, "test1"), new Entity(3, "test3"));

            var one = db.queryOne(
                JdbcQuery.named()
                    .sql("SELECT id, value FROM %s WHERE value = :value", tableName)
                    .bind("value", "test1")
                    .build(),
                mapper
            );
            Assertions.assertThat(one).isEqualTo(new Entity(1, "test1"));

            var optional = db.queryOptional(
                JdbcQuery.named()
                    .sql("SELECT id, value FROM %s WHERE value = :value", tableName)
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
                .sql("INSERT INTO %s(value) VALUES (:value)", tableName)
                .bind("value", "test1", JDBCType.VARCHAR)
                .build();
            Assertions.assertThat(db.executeUpdate(insert)).isEqualTo(new UpdateCount(1));

            var generatedId = db.executeUpdate(
                JdbcQuery.named()
                    .sql("INSERT INTO %s(value) VALUES (:value)", tableName)
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
                .sql("INSERT INTO %s(value) VALUES (:value)", tableName)
                .batch()
                .bind(Map.of("value", "test3"))
                .bind(row -> row.bind("value", "test4"))
                .build();
            Assertions.assertThat(db.executeUpdateBatch(namedBatch)).isEqualTo(new UpdateCount(2));

            var templateBatch = JdbcQuery.template()
                .sql("INSERT INTO %s(value) VALUES (?)", tableName)
                .batch()
                .bind("test5")
                .bindAll(List.of("test6"), (row, value) -> row.bind(value))
                .build();
            Assertions.assertThat(db.executeUpdateBatch(templateBatch)).isEqualTo(new UpdateCount(2));
        });
    }

    @Test
    void testTransaction(PostgresParams params) throws SQLException {
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
            var latch = new CountDownLatch(1);
            Assertions.assertThatThrownBy(() -> db.inTx((JdbcExecutor.SqlRunnable) () -> {
                db.currentContext().afterRollback((conn, e) -> latch.countDown());
                try (var stmt = db.connectionCurrent().prepareStatement(sql)) {
                    stmt.execute();
                }
                throw new RuntimeException();
            }));

            Assertions.assertThat(latch.getCount()).isEqualTo(0);

            var values = params.query("SELECT value FROM %s".formatted(tableName), extractor);
            Assertions.assertThat(values).hasSize(0);

            var latch1 = new CountDownLatch(1);
            db.inTx(() -> {
                db.currentContext().afterCommit((conn) -> latch1.countDown());
                try (var stmt = db.connectionCurrent().prepareStatement(sql)) {
                    stmt.execute();
                }
            });

            Assertions.assertThat(latch1.getCount()).isEqualTo(0);

            values = params.query("SELECT value FROM %s".formatted(tableName), extractor);
            Assertions.assertThat(values).hasSize(1);
        });
    }

    @Test
    void testTransactionIsolationLevel(PostgresParams params) throws SQLException {
        withDb(params, db -> {
            var previousIsolationLevel = db.withConnection(Connection::getTransactionIsolation);

            db.inTx(JdbcExecutor.TxIsolation.SERIALIZABLE, context -> {
                Assertions.assertThat(context.connection().getTransactionIsolation())
                    .isEqualTo(Connection.TRANSACTION_SERIALIZABLE);
            });

            var currentIsolationLevel = db.withConnection(Connection::getTransactionIsolation);
            Assertions.assertThat(currentIsolationLevel).isEqualTo(previousIsolationLevel);
        });
    }
}

package io.koraframework.database.jdbc;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

class JdbcQueryTest {

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
            .bindIn("ids", List.of(1L, 2L, 3L))
            .sqlIf(" ORDER BY id", true)
            .build();

        Assertions.assertThat(query.sourceSql())
            .isEqualTo("SELECT * FROM users WHERE 1 = 1 AND status = :status AND id IN (:ids) ORDER BY id");
        Assertions.assertThat(query.sql())
            .isEqualTo("SELECT * FROM users WHERE 1 = 1 AND status = ? AND id IN (?, ?, ?) ORDER BY id");
        Assertions.assertThat(query.parameters().stream().map(JdbcQuery.Parameter::value).toList())
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
            .bindIn("ids", List.of(1L, 2L), Types.BIGINT)
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
    void testJdbcNamedBindKeepsCollectionAsSingleParameter() {
        var values = List.of(1L, 2L);
        var query = JdbcQuery.named()
            .sql("SELECT :ids")
            .bind("ids", values)
            .build();

        Assertions.assertThat(query.sql()).isEqualTo("SELECT ?");
        Assertions.assertThat(query.parameters().stream().map(JdbcQuery.Parameter::value).toList())
            .containsExactly(values);
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
            .sql("SELECT * FROM %s WHERE name = ?".formatted("users"))
            .bindAll("alice")
            .sqlIf(" AND age > ?", true, 42)
            .sqlIf(" AND deleted = ?", false, true)
            .build();

        Assertions.assertThat(query.sourceSql()).isEqualTo("SELECT * FROM users WHERE name = ? AND age > ?");
        Assertions.assertThat(query.sql()).isEqualTo("SELECT * FROM users WHERE name = ? AND age > ?");
        Assertions.assertThat(query.parameters().stream().map(JdbcQuery.Parameter::value).toList())
            .containsExactly("alice", 42);

        try (var ignored = query.prepare(connection)) {
            Assertions.assertThat(calls)
                .containsExactly(
                    "setObject:1:alice",
                    "setObject:2:42"
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
            .sql("INSERT INTO %s(value, status) VALUES (:value, :status)".formatted("users"))
            .batch()
            .bindAll(List.of(first, second), JdbcQuery.NamedRowBinder::bindAll)
            .build();

        Assertions.assertThat(namedBatch.sourceSql()).isEqualTo("INSERT INTO users(value, status) VALUES (:value, :status)");
        Assertions.assertThat(namedBatch.sql()).isEqualTo("INSERT INTO users(value, status) VALUES (?, ?)");
        Assertions.assertThat(namedBatch.size()).isEqualTo(2);
        Assertions.assertThat(namedBatch.parameters(0).stream().map(JdbcQuery.Parameter::value)).containsExactly("test1", "active");
        Assertions.assertThat(namedBatch.parameters(1).stream().map(JdbcQuery.Parameter::value)).containsExactly("test2", "archived");

        var templateBatch = JdbcQuery.template()
            .sql("INSERT INTO %s(value, status) VALUES (?, ?)".formatted("users"))
            .batch()
            .bindAll(List.of(List.of("test1", "active"), List.of("test2", "archived")), (row, values) -> row.bindAll(values.toArray()))
            .build();

        Assertions.assertThat(templateBatch.sourceSql()).isEqualTo("INSERT INTO users(value, status) VALUES (?, ?)");
        Assertions.assertThat(templateBatch.sql()).isEqualTo("INSERT INTO users(value, status) VALUES (?, ?)");
        Assertions.assertThat(templateBatch.size()).isEqualTo(2);
        Assertions.assertThat(templateBatch.parameters(0).stream().map(JdbcQuery.Parameter::value)).containsExactly("test1", "active");
        Assertions.assertThat(templateBatch.parameters(1).stream().map(JdbcQuery.Parameter::value)).containsExactly("test2", "archived");
    }
}

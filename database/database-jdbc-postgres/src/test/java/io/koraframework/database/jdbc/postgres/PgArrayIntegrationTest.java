package io.koraframework.database.jdbc.postgres;

import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PostgresTestContainer.class)
class PgArrayIntegrationTest {

    private final PostgresJdbcDatabaseModule module = new PostgresJdbcDatabaseModule() {};

    @Test
    void arrayRoundTripForEveryElementType(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (c_bool bool[], c_int2 int2[], c_int4 int4[],"
                                                 + " c_int8 int8[], c_float4 float4[], c_float8 float8[],"
                                                 + " c_numeric numeric[], c_varchar varchar[], c_uuid uuid[])");

            var booleans = List.of(true, false);
            var shorts = List.of((short) 1, (short) 2);
            var integers = List.of(1, 2);
            var longs = List.of(1L, 2L);
            var floats = List.of(1.5f, 2.5f);
            var doubles = List.of(1.5d, 2.5d);
            var decimals = List.of(new BigDecimal("1.50"), new BigDecimal("2.50"));
            var strings = List.of("a", "b");
            var uuids = List.of(UUID.randomUUID(), UUID.randomUUID());

            try (var stmt = connection.prepareStatement("INSERT INTO t VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                module.booleanListJdbcParameterColumnMapper().set(stmt, 1, booleans);
                module.shortListJdbcParameterColumnMapper().set(stmt, 2, shorts);
                module.integerListJdbcParameterColumnMapper().set(stmt, 3, integers);
                module.longListJdbcParameterColumnMapper().set(stmt, 4, longs);
                module.floatListJdbcParameterColumnMapper().set(stmt, 5, floats);
                module.doubleListJdbcParameterColumnMapper().set(stmt, 6, doubles);
                module.bigDecimalListJdbcParameterColumnMapper().set(stmt, 7, decimals);
                module.stringListJdbcParameterColumnMapper().set(stmt, 8, strings);
                module.uuidListJdbcParameterColumnMapper().set(stmt, 9, uuids);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT * FROM t");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.booleanListJdbcResultColumnMapper().apply(rs, 1)).isEqualTo(booleans);
                assertThat(module.shortListJdbcResultColumnMapper().apply(rs, 2)).isEqualTo(shorts);
                assertThat(module.integerListJdbcResultColumnMapper().apply(rs, 3)).isEqualTo(integers);
                assertThat(module.longListJdbcResultColumnMapper().apply(rs, 4)).isEqualTo(longs);
                assertThat(module.floatListJdbcResultColumnMapper().apply(rs, 5)).isEqualTo(floats);
                assertThat(module.doubleListJdbcResultColumnMapper().apply(rs, 6)).isEqualTo(doubles);
                assertThat(module.bigDecimalListJdbcResultColumnMapper().apply(rs, 7)).isEqualTo(decimals);
                assertThat(module.stringListJdbcResultColumnMapper().apply(rs, 8)).isEqualTo(strings);
                assertThat(module.uuidListJdbcResultColumnMapper().apply(rs, 9)).isEqualTo(uuids);
            }
        }
    }

    @Test
    void nullAndNullElementsRoundTrip(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (id int, c_varchar varchar[])");
            var withNulls = Arrays.asList("a", null, "b");

            try (var stmt = connection.prepareStatement("INSERT INTO t VALUES (?, ?)")) {
                stmt.setInt(1, 1);
                module.stringListJdbcParameterColumnMapper().set(stmt, 2, null);
                stmt.executeUpdate();

                stmt.setInt(1, 2);
                module.stringListJdbcParameterColumnMapper().set(stmt, 2, withNulls);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT c_varchar FROM t ORDER BY id");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.stringListJdbcResultColumnMapper().apply(rs, 1)).isNull();
                assertThat(rs.next()).isTrue();
                assertThat(module.stringListJdbcResultColumnMapper().apply(rs, 1)).isEqualTo(withNulls);
            }
        }
    }

    @Test
    void emptyArrayRoundTrip(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (c_int4 int4[])");

            try (var stmt = connection.prepareStatement("INSERT INTO t VALUES (?)")) {
                module.integerListJdbcParameterColumnMapper().set(stmt, 1, List.of());
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT c_int4 FROM t");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.integerListJdbcResultColumnMapper().apply(rs, 1)).isEmpty();
            }
        }
    }

    @Test
    void setAndCollectionParametersRoundTrip(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (id int, c_varchar varchar[])");
            Set<String> set = new LinkedHashSet<>(Arrays.asList("a", null, "b"));
            Collection<String> collection = Arrays.asList("x", "y");

            try (var stmt = connection.prepareStatement("INSERT INTO t VALUES (?, ?)")) {
                stmt.setInt(1, 1);
                module.stringSetJdbcParameterColumnMapper().set(stmt, 2, set);
                stmt.executeUpdate();

                stmt.setInt(1, 2);
                module.stringCollectionJdbcParameterColumnMapper().set(stmt, 2, collection);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT c_varchar FROM t ORDER BY id");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.stringListJdbcResultColumnMapper().apply(rs, 1)).containsExactly("a", null, "b");
                assertThat(rs.next()).isTrue();
                assertThat(module.stringListJdbcResultColumnMapper().apply(rs, 1)).containsExactly("x", "y");
            }
        }
    }

    @Test
    void javaArrayRoundTrip(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (id int, c_varchar varchar[], c_int4 int4[])");
            var strings = new String[]{"a", null, "b"};
            var integers = new Integer[]{1, 2, 3};

            try (var stmt = connection.prepareStatement("INSERT INTO t VALUES (?, ?, ?)")) {
                stmt.setInt(1, 1);
                module.stringArrayJdbcParameterColumnMapper().set(stmt, 2, strings);
                module.integerArrayJdbcParameterColumnMapper().set(stmt, 3, integers);
                stmt.executeUpdate();

                stmt.setInt(1, 2);
                module.stringArrayJdbcParameterColumnMapper().set(stmt, 2, null);
                module.integerArrayJdbcParameterColumnMapper().set(stmt, 3, null);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT c_varchar, c_int4 FROM t ORDER BY id");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.stringArrayJdbcResultColumnMapper().apply(rs, 1)).containsExactly(strings);
                assertThat(module.integerArrayJdbcResultColumnMapper().apply(rs, 2)).containsExactly(integers);
                assertThat(rs.next()).isTrue();
                assertThat(module.stringArrayJdbcResultColumnMapper().apply(rs, 1)).isNull();
                assertThat(module.integerArrayJdbcResultColumnMapper().apply(rs, 2)).isNull();
            }
        }
    }

    @Test
    void arrayAsInListFilter(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (id bigint)");
            connection.createStatement().execute("INSERT INTO t(id) VALUES (1), (2), (3), (4)");

            try (var stmt = connection.prepareStatement("SELECT count(*) FROM t WHERE id = ANY(?)")) {
                module.longSetJdbcParameterColumnMapper().set(stmt, 1, new LinkedHashSet<>(List.of(2L, 4L, 99L)));
                try (var rs = stmt.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                }
            }
        }
    }
}

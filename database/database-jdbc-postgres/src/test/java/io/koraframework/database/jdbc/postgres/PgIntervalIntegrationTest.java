package io.koraframework.database.jdbc.postgres;

import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Period;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(PostgresTestContainer.class)
class PgIntervalIntegrationTest {

    private final PostgresJdbcDatabaseModule module = new PostgresJdbcDatabaseModule() {};

    @Test
    void durationRoundTrip(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (id int, c_interval interval)");
            var duration = Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4).plusNanos(500_000_000);

            try (var stmt = connection.prepareStatement("INSERT INTO t VALUES (?, ?)")) {
                stmt.setInt(1, 1);
                module.durationPostgresJdbcParameterColumnMapper().set(stmt, 2, duration);
                stmt.executeUpdate();

                stmt.setInt(1, 2);
                module.durationPostgresJdbcParameterColumnMapper().set(stmt, 2, null);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT c_interval FROM t ORDER BY id");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.durationPostgresJdbcResultColumnMapper().apply(rs, 1)).isEqualTo(duration);
                assertThat(rs.next()).isTrue();
                assertThat(module.durationPostgresJdbcResultColumnMapper().apply(rs, 1)).isNull();
            }
        }
    }

    @Test
    void negativeDurationRoundTrip(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (c_interval interval)");
            var duration = Duration.ofHours(-2).minusMinutes(3).minusNanos(500_000_000);

            try (var stmt = connection.prepareStatement("INSERT INTO t VALUES (?)")) {
                module.durationPostgresJdbcParameterColumnMapper().set(stmt, 1, duration);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT c_interval FROM t");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.durationPostgresJdbcResultColumnMapper().apply(rs, 1)).isEqualTo(duration);
            }
        }
    }

    @Test
    void periodRoundTrip(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (id int, c_interval interval)");
            var period = Period.of(1, 2, 3);

            try (var stmt = connection.prepareStatement("INSERT INTO t VALUES (?, ?)")) {
                stmt.setInt(1, 1);
                module.periodPostgresJdbcParameterColumnMapper().set(stmt, 2, period);
                stmt.executeUpdate();

                stmt.setInt(1, 2);
                module.periodPostgresJdbcParameterColumnMapper().set(stmt, 2, null);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT c_interval FROM t ORDER BY id");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.periodPostgresJdbcResultColumnMapper().apply(rs, 1)).isEqualTo(period);
                assertThat(rs.next()).isTrue();
                assertThat(module.periodPostgresJdbcResultColumnMapper().apply(rs, 1)).isNull();
            }
        }
    }

    @Test
    void failsInsteadOfLosingDataOnIncompatibleInterval(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (c_interval interval)");
            connection.createStatement().execute("INSERT INTO t VALUES ('1 mon 5 hours')");

            try (var stmt = connection.prepareStatement("SELECT c_interval FROM t");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThatThrownBy(() -> module.durationPostgresJdbcResultColumnMapper().apply(rs, 1))
                    .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> module.periodPostgresJdbcResultColumnMapper().apply(rs, 1))
                    .isInstanceOf(SQLException.class);
            }
        }
    }

    @Test
    void readsIntervalsUnderEveryIntervalStyle(PostgresParams params) throws SQLException {
        for (var style : List.of("postgres", "postgres_verbose", "sql_standard", "iso_8601")) {
            try (var connection = params.createConnection()) {
                connection.createStatement().execute("SET intervalstyle = '" + style + "'");

                assertThat(read(connection, "SELECT interval '1 day 02:03:04.5'", module.durationPostgresJdbcResultColumnMapper()))
                    .as(style).isEqualTo(Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4).plusMillis(500));
                assertThat(read(connection, "SELECT interval '-1 day -02:03:04.5'", module.durationPostgresJdbcResultColumnMapper()))
                    .as(style).isEqualTo(Duration.ofDays(-1).minusHours(2).minusMinutes(3).minusSeconds(4).minusMillis(500));
                assertThat(read(connection, "SELECT interval '-02:03:04.5'", module.durationPostgresJdbcResultColumnMapper()))
                    .as(style).isEqualTo(Duration.ofHours(-2).minusMinutes(3).minusSeconds(4).minusMillis(500));
                assertThat(read(connection, "SELECT interval '1 day -00:00:01'", module.durationPostgresJdbcResultColumnMapper()))
                    .as(style).isEqualTo(Duration.ofDays(1).minusSeconds(1));
                assertThat(read(connection, "SELECT interval '0'", module.durationPostgresJdbcResultColumnMapper()))
                    .as(style).isEqualTo(Duration.ZERO);
                assertThat(read(connection, "SELECT interval '1 year 2 mons 3 days'", module.periodPostgresJdbcResultColumnMapper()))
                    .as(style).isEqualTo(Period.of(1, 2, 3));
                assertThat(read(connection, "SELECT interval '-1 year -2 mons'", module.periodPostgresJdbcResultColumnMapper()))
                    .as(style).isEqualTo(Period.of(-1, -2, 0));
                assertThat(read(connection, "SELECT interval '5 days'", module.periodPostgresJdbcResultColumnMapper()))
                    .as(style).isEqualTo(Period.ofDays(5));
                assertThatThrownBy(() -> read(connection, "SELECT interval '1 mon 5 hours'", module.durationPostgresJdbcResultColumnMapper()))
                    .as(style).isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> read(connection, "SELECT interval '1 mon 5 hours'", module.periodPostgresJdbcResultColumnMapper()))
                    .as(style).isInstanceOf(SQLException.class);
            }
        }
    }

    private static <T> T read(Connection connection, String sql, JdbcResultColumnMapper<T> mapper) throws SQLException {
        try (var stmt = connection.prepareStatement(sql); var rs = stmt.executeQuery()) {
            assertThat(rs.next()).isTrue();
            return mapper.apply(rs, 1);
        }
    }
}

package io.koraframework.database.jdbc.postgres;

import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Period;

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
                module.durationJdbcParameterColumnMapper().set(stmt, 2, duration);
                stmt.executeUpdate();

                stmt.setInt(1, 2);
                module.durationJdbcParameterColumnMapper().set(stmt, 2, null);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT c_interval FROM t ORDER BY id");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.durationJdbcResultColumnMapper().apply(rs, 1)).isEqualTo(duration);
                assertThat(rs.next()).isTrue();
                assertThat(module.durationJdbcResultColumnMapper().apply(rs, 1)).isNull();
            }
        }
    }

    @Test
    void negativeDurationRoundTrip(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (c_interval interval)");
            var duration = Duration.ofHours(-2).minusMinutes(3).minusNanos(500_000_000);

            try (var stmt = connection.prepareStatement("INSERT INTO t VALUES (?)")) {
                module.durationJdbcParameterColumnMapper().set(stmt, 1, duration);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT c_interval FROM t");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.durationJdbcResultColumnMapper().apply(rs, 1)).isEqualTo(duration);
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
                module.periodJdbcParameterColumnMapper().set(stmt, 2, period);
                stmt.executeUpdate();

                stmt.setInt(1, 2);
                module.periodJdbcParameterColumnMapper().set(stmt, 2, null);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT c_interval FROM t ORDER BY id");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.periodJdbcResultColumnMapper().apply(rs, 1)).isEqualTo(period);
                assertThat(rs.next()).isTrue();
                assertThat(module.periodJdbcResultColumnMapper().apply(rs, 1)).isNull();
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
                assertThatThrownBy(() -> module.durationJdbcResultColumnMapper().apply(rs, 1))
                    .isInstanceOf(SQLException.class);
                assertThatThrownBy(() -> module.periodJdbcResultColumnMapper().apply(rs, 1))
                    .isInstanceOf(SQLException.class);
            }
        }
    }
}

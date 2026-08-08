package io.koraframework.database.jdbc.postgres;

import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PostgresTestContainer.class)
class PgRangeIntegrationTest {

    private final PostgresJdbcDatabaseModule module = new PostgresJdbcDatabaseModule() {};

    @Test
    void rangeRoundTripForEveryRangeType(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (c_int4 int4range, c_int8 int8range,"
                                                 + " c_num numrange, c_date daterange, c_ts tsrange)");

            var int4 = PgRange.closedOpen(1, 10);
            var int8 = PgRange.closedOpen(1L, 10L);
            var num = PgRange.closedOpen(new BigDecimal("1.50"), new BigDecimal("2.50"));
            var date = PgRange.closedOpen(LocalDate.of(2021, 1, 1), LocalDate.of(2021, 12, 31));
            var ts = PgRange.closedOpen(
                LocalDateTime.of(2021, 1, 1, 10, 0, 0, 123_456_000),
                LocalDateTime.of(2021, 12, 31, 23, 59, 59));

            try (var stmt = connection.prepareStatement("INSERT INTO t VALUES (?, ?, ?, ?, ?)")) {
                module.int4RangeJdbcParameterColumnMapper().set(stmt, 1, int4);
                module.int8RangeJdbcParameterColumnMapper().set(stmt, 2, int8);
                module.numRangeJdbcParameterColumnMapper().set(stmt, 3, num);
                module.dateRangeJdbcParameterColumnMapper().set(stmt, 4, date);
                module.tsRangeJdbcParameterColumnMapper().set(stmt, 5, ts);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT * FROM t");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.int4RangeJdbcResultColumnMapper().apply(rs, 1)).isEqualTo(int4);
                assertThat(module.int8RangeJdbcResultColumnMapper().apply(rs, 2)).isEqualTo(int8);
                assertThat(module.numRangeJdbcResultColumnMapper().apply(rs, 3)).isEqualTo(num);
                assertThat(module.dateRangeJdbcResultColumnMapper().apply(rs, 4)).isEqualTo(date);
                assertThat(module.tsRangeJdbcResultColumnMapper().apply(rs, 5)).isEqualTo(ts);
            }
        }
    }

    @Test
    void timestampWithTimezoneRangeRoundTripInNonUtcSession(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            // PostgreSQL отдаёт границы tstzrange в таймзоне сессии, offset вида "+03" без минут
            connection.createStatement().execute("SET TIME ZONE 'Europe/Moscow'");
            connection.createStatement().execute("CREATE TABLE t (c_tstz tstzrange)");
            var tstz = PgRange.closedOpen(
                OffsetDateTime.of(2021, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2021, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC));

            try (var stmt = connection.prepareStatement("INSERT INTO t VALUES (?)")) {
                module.tstzRangeJdbcParameterColumnMapper().set(stmt, 1, tstz);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT c_tstz FROM t");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                var read = Objects.requireNonNull(module.tstzRangeJdbcResultColumnMapper().apply(rs, 1));
                var lower = Objects.requireNonNull(read.lower());
                var upper = Objects.requireNonNull(read.upper());
                assertThat(lower.toInstant()).isEqualTo(Objects.requireNonNull(tstz.lower()).toInstant());
                assertThat(upper.toInstant()).isEqualTo(Objects.requireNonNull(tstz.upper()).toInstant());
                assertThat(read.lowerInclusive()).isTrue();
                assertThat(read.upperInclusive()).isFalse();
            }
        }
    }

    @Test
    void emptyUnboundedAndNullRoundTrip(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (id int, c_int4 int4range)");

            try (var stmt = connection.prepareStatement("INSERT INTO t VALUES (?, ?)")) {
                stmt.setInt(1, 1);
                module.int4RangeJdbcParameterColumnMapper().set(stmt, 2, PgRange.empty());
                stmt.executeUpdate();

                stmt.setInt(1, 2);
                module.int4RangeJdbcParameterColumnMapper().set(stmt, 2, PgRange.open(null, null));
                stmt.executeUpdate();

                stmt.setInt(1, 3);
                module.int4RangeJdbcParameterColumnMapper().set(stmt, 2, null);
                stmt.executeUpdate();
            }

            try (var stmt = connection.prepareStatement("SELECT c_int4 FROM t ORDER BY id");
                 var rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(module.int4RangeJdbcResultColumnMapper().apply(rs, 1)).isEqualTo(PgRange.<Integer>empty());
                assertThat(rs.next()).isTrue();
                assertThat(module.int4RangeJdbcResultColumnMapper().apply(rs, 1))
                    .isEqualTo(PgRange.<Integer>open(null, null));
                assertThat(rs.next()).isTrue();
                assertThat(module.int4RangeJdbcResultColumnMapper().apply(rs, 1)).isNull();
            }
        }
    }

    @Test
    void rangeContainmentQuery(PostgresParams params) throws SQLException {
        try (var connection = params.createConnection()) {
            connection.createStatement().execute("CREATE TABLE t (c_int4 int4range)");
            connection.createStatement().execute("INSERT INTO t VALUES ('[1,100)')");

            try (var stmt = connection.prepareStatement("SELECT count(*) FROM t WHERE c_int4 @> ?")) {
                module.int4RangeJdbcParameterColumnMapper().set(stmt, 1, PgRange.closedOpen(10, 20));
                try (var rs = stmt.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }
}

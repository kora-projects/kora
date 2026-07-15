package io.koraframework.database.jdbc.postgres;

import io.koraframework.database.jdbc.mapper.parameter.CollectionParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.CollectionFromSqlArrayResultColumnMapper;
import io.koraframework.database.jdbc.mapper.result.ListCollectionFactory;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(PostgresTestContainer.class)
class PostgresIntegrationTest {

    private final PgIntervalJdbcMappersModule durationModule = new PgIntervalJdbcMappersModule() {};
    private final PostgresArrayColumnDataModule arrayModule = new PostgresArrayColumnDataModule() {};
    private final PostgresRangeJdbcMappersModule rangeModule = new PostgresRangeJdbcMappersModule() {};

    @Test
    void durationRoundTripViaInterval(PostgresParams params) throws SQLException {
        try (var conn = params.createConnection()) {
            conn.createStatement().execute("CREATE TABLE t (id int, dur interval)");
            var duration = Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4);

            try (var ps = conn.prepareStatement("INSERT INTO t(id, dur) VALUES (1, ?)")) {
                durationModule.durationJdbcParameterColumnMapper().set(ps, 1, duration);
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("SELECT dur FROM t WHERE id = 1");
                 var rs = ps.executeQuery()) {
                rs.next();
                var read = durationModule.durationJdbcResultColumnMapper().apply(rs, 1);
                assertThat(read).isEqualTo(duration);
            }
        }
    }

    @Test
    void uuidArrayRoundTrip(PostgresParams params) throws SQLException {
        try (var conn = params.createConnection()) {
            conn.createStatement().execute("CREATE TABLE t (id int, ids uuid[])");
            var ids = List.of(UUID.randomUUID(), UUID.randomUUID());
            var paramMapper = new CollectionParameterColumnMapper<UUID, List<UUID>>(arrayModule.uuidArrayColumnData());
            var resultMapper = new CollectionFromSqlArrayResultColumnMapper<UUID, List<UUID>>(
                arrayModule.uuidArrayColumnData(), new ListCollectionFactory<>());

            try (var ps = conn.prepareStatement("INSERT INTO t(id, ids) VALUES (1, ?)")) {
                paramMapper.set(ps, 1, ids);
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("SELECT ids FROM t WHERE id = 1");
                 var rs = ps.executeQuery()) {
                rs.next();
                assertThat(resultMapper.apply(rs, 1)).containsExactlyElementsOf(ids);
            }
        }
    }

    @Test
    void anyArrayForInListFilter(PostgresParams params) throws SQLException {
        try (var conn = params.createConnection()) {
            conn.createStatement().execute("CREATE TABLE t (id bigint)");
            conn.createStatement().execute("INSERT INTO t(id) VALUES (1), (2), (3), (4)");
            var wanted = List.of(2L, 4L, 99L);
            var paramMapper = new CollectionParameterColumnMapper<Long, List<Long>>(arrayModule.longArrayColumnData());

            try (var ps = conn.prepareStatement("SELECT count(*) FROM t WHERE id = ANY(?)")) {
                paramMapper.set(ps, 1, wanted);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                }
            }
        }
    }

    @Test
    void int4RangeRoundTrip(PostgresParams params) throws SQLException {
        try (var conn = params.createConnection()) {
            conn.createStatement().execute("CREATE TABLE t (id int, r int4range)");
            var range = Range.closedOpen(1, 10);
            var paramMapper = rangeModule.rangeJdbcParameterColumnMapper(rangeModule.int4RangeColumnData());
            var resultMapper = rangeModule.rangeJdbcResultColumnMapper(rangeModule.int4RangeColumnData());

            try (var ps = conn.prepareStatement("INSERT INTO t(id, r) VALUES (1, ?)")) {
                paramMapper.set(ps, 1, range);
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("SELECT r FROM t WHERE id = 1");
                 var rs = ps.executeQuery()) {
                rs.next();
                assertThat(resultMapper.apply(rs, 1)).isEqualTo(range);
            }
        }
    }

    @Test
    void rangeContainmentQuery(PostgresParams params) throws SQLException {
        try (var conn = params.createConnection()) {
            conn.createStatement().execute("CREATE TABLE t (id int, r int4range)");
            conn.createStatement().execute("INSERT INTO t(id, r) VALUES (1, '[1,100)')");
            var paramMapper = rangeModule.rangeJdbcParameterColumnMapper(rangeModule.int4RangeColumnData());

            try (var ps = conn.prepareStatement("SELECT count(*) FROM t WHERE r @> ?")) {
                paramMapper.set(ps, 1, Range.closedOpen(10, 20));
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    void tsRangeRoundTrip(PostgresParams params) throws SQLException {
        try (var conn = params.createConnection()) {
            conn.createStatement().execute("CREATE TABLE t (id int, r tsrange)");
            var range = Range.closedOpen(
                LocalDateTime.of(2021, 1, 1, 10, 0, 0),
                LocalDateTime.of(2021, 12, 31, 23, 59, 59));
            var paramMapper = rangeModule.rangeJdbcParameterColumnMapper(rangeModule.tsRangeColumnData());
            var resultMapper = rangeModule.rangeJdbcResultColumnMapper(rangeModule.tsRangeColumnData());

            try (var ps = conn.prepareStatement("INSERT INTO t(id, r) VALUES (1, ?)")) {
                paramMapper.set(ps, 1, range);
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("SELECT r FROM t WHERE id = 1");
                 var rs = ps.executeQuery()) {
                rs.next();
                assertThat(resultMapper.apply(rs, 1)).isEqualTo(range);
            }
        }
    }

    @Test
    void tstzRangeRoundTrip(PostgresParams params) throws SQLException {
        try (var conn = params.createConnection()) {
            conn.createStatement().execute("SET TIME ZONE 'UTC'");
            conn.createStatement().execute("CREATE TABLE t (id int, r tstzrange)");
            var range = Range.closedOpen(
                OffsetDateTime.of(2021, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2021, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC));
            var paramMapper = rangeModule.rangeJdbcParameterColumnMapper(rangeModule.tstzRangeColumnData());
            var resultMapper = rangeModule.rangeJdbcResultColumnMapper(rangeModule.tstzRangeColumnData());

            try (var ps = conn.prepareStatement("INSERT INTO t(id, r) VALUES (1, ?)")) {
                paramMapper.set(ps, 1, range);
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("SELECT r FROM t WHERE id = 1");
                 var rs = ps.executeQuery()) {
                rs.next();
                assertThat(resultMapper.apply(rs, 1)).isEqualTo(range);
            }
        }
    }

    @Test
    void periodRoundTripViaInterval(PostgresParams params) throws SQLException {
        try (var conn = params.createConnection()) {
            conn.createStatement().execute("CREATE TABLE t (id int, p interval)");
            var period = Period.of(1, 2, 3);

            try (var ps = conn.prepareStatement("INSERT INTO t(id, p) VALUES (1, ?)")) {
                durationModule.periodJdbcParameterColumnMapper().set(ps, 1, period);
                ps.executeUpdate();
            }
            try (var ps = conn.prepareStatement("SELECT p FROM t WHERE id = 1");
                 var rs = ps.executeQuery()) {
                rs.next();
                assertThat(durationModule.periodJdbcResultColumnMapper().apply(rs, 1)).isEqualTo(period);
            }
        }
    }
}

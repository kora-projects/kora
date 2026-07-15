package io.koraframework.database.jdbc.postgres;

import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcRowMapper;
import org.postgresql.util.PGInterval;

import java.sql.Types;
import java.time.Duration;
import java.time.Period;

public interface PgIntervalJdbcMappersModule {

    default JdbcParameterColumnMapper<Duration> durationJdbcParameterColumnMapper() {
        return (stmt, index, duration) -> {
            if (duration == null) {
                stmt.setNull(index, Types.OTHER);
                return;
            }
            var days = (int) duration.toDays();
            var hours = (int) (duration.toHours() - days * 24L);
            var mins = (int) (duration.toMinutes() - duration.toHours() * 60L);
            var secs = (int) (duration.toSeconds() - duration.toMinutes() * 60L);
            stmt.setObject(index, new PGInterval(0, 0, days, hours, mins, secs));
        };
    }

    default JdbcResultColumnMapper<Duration> durationJdbcResultColumnMapper() {
        return (row, index) -> {
            var interval = row.getObject(index, PGInterval.class);
            if (row.wasNull()) {
                return null;
            }
            return Duration.ofDays(interval.getDays())
                .plusHours(interval.getHours())
                .plusMinutes(interval.getMinutes())
                .plusNanos(Math.round(interval.getSeconds() * 1_000_000_000.0));
        };
    }

    default JdbcRowMapper<Duration> durationJdbcRowMapper(JdbcResultColumnMapper<Duration> resultColumnMapper) {
        return row -> resultColumnMapper.apply(row, 1);
    }

    default JdbcParameterColumnMapper<Period> periodJdbcParameterColumnMapper() {
        return (stmt, index, period) -> {
            if (period == null) {
                stmt.setNull(index, Types.OTHER);
                return;
            }
            stmt.setObject(index, new PGInterval(period.getYears(), period.getMonths(), period.getDays(), 0, 0, 0));
        };
    }

    default JdbcResultColumnMapper<Period> periodJdbcResultColumnMapper() {
        return (row, index) -> {
            var interval = row.getObject(index, PGInterval.class);
            if (row.wasNull()) {
                return null;
            }
            return Period.of(interval.getYears(), interval.getMonths(), interval.getDays());
        };
    }

    default JdbcRowMapper<Period> periodJdbcRowMapper(JdbcResultColumnMapper<Period> resultColumnMapper) {
        return row -> resultColumnMapper.apply(row, 1);
    }
}

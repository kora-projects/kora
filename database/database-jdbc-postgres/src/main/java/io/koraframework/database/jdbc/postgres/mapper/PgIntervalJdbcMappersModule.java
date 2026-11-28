package io.koraframework.database.jdbc.postgres.mapper;

import io.koraframework.common.annotation.*;
import io.koraframework.database.jdbc.mapper.parameter.*;
import io.koraframework.database.jdbc.mapper.result.*;
import io.koraframework.database.jdbc.postgres.annotation.*;
import org.jspecify.annotations.*;
import org.postgresql.util.*;

import java.sql.*;
import java.time.*;

/**
 * <b>Русский</b>: Конвертеры {@link Duration} и {@link Period} в колонку типа {@code interval}.
 * <hr>
 * <b>English</b>: Converters of {@link Duration} and {@link Period} into an {@code interval} column.
 */
public interface PgIntervalJdbcMappersModule {

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Duration> durationPostgresJdbcParameterColumnMapper() {
        return Mappers.DURATION_PARAM;
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Duration> durationPostgresJdbcResultColumnMapper() {
        return Mappers.DURATION_RESULT;
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Period> periodPostgresJdbcParameterColumnMapper() {
        return Mappers.PERIOD_PARAM;
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Period> periodPostgresJdbcResultColumnMapper() {
        return Mappers.PERIOD_RESULT;
    }

    class Mappers {
        public static final DurationParamMapper DURATION_PARAM = new DurationParamMapper();
        public static final DurationResultMapper DURATION_RESULT = new DurationResultMapper();
        public static final PeriodParamMapper PERIOD_PARAM = new PeriodParamMapper();
        public static final PeriodResultMapper PERIOD_RESULT = new PeriodResultMapper();

        public static final class DurationParamMapper implements JdbcParameterColumnMapper<Duration> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable Duration value) throws SQLException {
                if (value == null) {
                    stmt.setNull(index, Types.OTHER);
                    return;
                }
                var seconds = value.toSecondsPart() + value.toNanosPart() / 1_000_000_000d;
                stmt.setObject(index, new PGInterval(0, 0, Math.toIntExact(value.toDaysPart()),
                    value.toHoursPart(), value.toMinutesPart(), seconds));
            }
        }

        public static final class DurationResultMapper implements JdbcResultColumnMapper<Duration> {
            @Override
            public @Nullable Duration apply(ResultSet row, int index) throws SQLException {
                var interval = row.getObject(index, PGInterval.class);
                if (row.wasNull()) {
                    return null;
                }
                if (interval.getYears() != 0 || interval.getMonths() != 0) {
                    throw new SQLException("PostgreSQL interval with years or months can't be converted to Duration, "
                        + "use Period instead: " + interval.getValue());
                }
                return Duration.ofDays(interval.getDays())
                    .plusHours(interval.getHours())
                    .plusMinutes(interval.getMinutes())
                    .plusSeconds(interval.getWholeSeconds())
                    .plusNanos(interval.getMicroSeconds() * 1_000L);
            }
        }

        public static final class PeriodParamMapper implements JdbcParameterColumnMapper<Period> {
            @Override
            public void set(PreparedStatement stmt, int index, @Nullable Period value) throws SQLException {
                if (value == null) {
                    stmt.setNull(index, Types.OTHER);
                    return;
                }
                stmt.setObject(index, new PGInterval(value.getYears(), value.getMonths(), value.getDays(), 0, 0, 0));
            }
        }

        public static final class PeriodResultMapper implements JdbcResultColumnMapper<Period> {
            @Override
            public @Nullable Period apply(ResultSet row, int index) throws SQLException {
                var interval = row.getObject(index, PGInterval.class);
                if (row.wasNull()) {
                    return null;
                }
                if (interval.getHours() != 0 || interval.getMinutes() != 0
                    || interval.getWholeSeconds() != 0 || interval.getMicroSeconds() != 0) {
                    throw new SQLException("PostgreSQL interval with a time part can't be converted to Period, "
                        + "use Duration instead: " + interval.getValue());
                }
                return Period.of(interval.getYears(), interval.getMonths(), interval.getDays());
            }
        }
    }
}

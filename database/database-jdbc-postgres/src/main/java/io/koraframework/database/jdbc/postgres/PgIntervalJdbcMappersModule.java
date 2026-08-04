package io.koraframework.database.jdbc.postgres;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.postgres.annotation.Pg;
import org.postgresql.util.PGInterval;

import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Period;

/**
 * <b>Русский</b>: Конвертеры {@link Duration} и {@link Period} в колонку типа {@code interval}.
 * <hr>
 * <b>English</b>: Converters of {@link Duration} and {@link Period} into an {@code interval} column.
 */
public interface PgIntervalJdbcMappersModule {

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Duration> durationJdbcParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setNull(index, Types.OTHER);
                return;
            }

            var seconds = value.toSecondsPart() + value.toNanosPart() / 1_000_000_000d;
            stmt.setObject(index, new PGInterval(0, 0, Math.toIntExact(value.toDaysPart()),
                value.toHoursPart(), value.toMinutesPart(), seconds));
        };
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Duration> durationJdbcResultColumnMapper() {
        return (row, index) -> {
            var interval = row.getObject(index, PGInterval.class);
            if (row.wasNull()) {
                return null;
            }
            // месяцы и годы не имеют фиксированной длины, молча отбросить их значит потерять данные
            if (interval.getYears() != 0 || interval.getMonths() != 0) {
                throw new SQLException("PostgreSQL interval with years or months can't be converted to Duration, "
                                       + "use Period instead: " + interval.getValue());
            }

            return Duration.ofDays(interval.getDays())
                .plusHours(interval.getHours())
                .plusMinutes(interval.getMinutes())
                .plusSeconds(interval.getWholeSeconds())
                .plusNanos(interval.getMicroSeconds() * 1_000L);
        };
    }

    @Pg
    @DefaultComponent
    default JdbcParameterColumnMapper<Period> periodJdbcParameterColumnMapper() {
        return (stmt, index, value) -> {
            if (value == null) {
                stmt.setNull(index, Types.OTHER);
                return;
            }

            stmt.setObject(index, new PGInterval(value.getYears(), value.getMonths(), value.getDays(), 0, 0, 0));
        };
    }

    @Pg
    @DefaultComponent
    default JdbcResultColumnMapper<Period> periodJdbcResultColumnMapper() {
        return (row, index) -> {
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
        };
    }
}

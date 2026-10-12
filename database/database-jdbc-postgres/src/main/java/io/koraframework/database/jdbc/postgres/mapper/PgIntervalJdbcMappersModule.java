package io.koraframework.database.jdbc.postgres.mapper;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.postgres.annotation.Pg;
import org.postgresql.util.PGInterval;

import java.sql.ResultSet;
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
    default JdbcParameterColumnMapper<Duration> durationPostgresJdbcParameterColumnMapper() {
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
    default JdbcResultColumnMapper<Duration> durationPostgresJdbcResultColumnMapper() {
        return (row, index) -> {
            var interval = readInterval(row, index);
            if (interval == null) {
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
    default JdbcParameterColumnMapper<Period> periodPostgresJdbcParameterColumnMapper() {
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
    default JdbcResultColumnMapper<Period> periodPostgresJdbcResultColumnMapper() {
        return (row, index) -> {
            var interval = readInterval(row, index);
            if (interval == null) {
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

    /**
     * PGInterval не понимает вывод IntervalStyle=sql_standard (например {@code 1 2:03:04.5}) и молча возвращает нули,
     * поэтому формат без букв и @ разбираем сами: {@code [+-]Y-M [+-]D [+-]H:MM:SS[.f]}, ведущий знак без явных знаков
     * у остальных полей относится ко всему интервалу.
     */
    private static PGInterval readInterval(ResultSet row, int index) throws SQLException {
        var text = row.getString(index);
        if (text == null) {
            return null;
        }
        if (text.chars().anyMatch(c -> Character.isLetter(c) || c == '@')) {
            return new PGInterval(text);
        }
        try {
            int years = 0, months = 0, days = 0, hours = 0, minutes = 0;
            double seconds = 0;
            var sign = 1;
            var tokens = text.trim().split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                var token = tokens[i];
                var tokenSign = i == 0 ? 1 : sign;
                if (token.startsWith("-") || token.startsWith("+")) {
                    tokenSign = token.charAt(0) == '-' ? -1 : 1;
                    token = token.substring(1);
                }
                if (i == 0) {
                    sign = tokenSign;
                }
                if (token.contains(":")) {
                    var parts = token.split(":");
                    hours = tokenSign * Integer.parseInt(parts[0]);
                    minutes = tokenSign * Integer.parseInt(parts[1]);
                    seconds = parts.length > 2 ? tokenSign * Double.parseDouble(parts[2]) : 0;
                } else if (token.contains("-")) {
                    var parts = token.split("-");
                    years = tokenSign * Integer.parseInt(parts[0]);
                    months = tokenSign * Integer.parseInt(parts[1]);
                } else {
                    days = tokenSign * Integer.parseInt(token);
                }
            }
            return new PGInterval(years, months, days, hours, minutes, seconds);
        } catch (RuntimeException e) {
            throw new SQLException("Can't parse PostgreSQL interval: " + text, e);
        }
    }
}

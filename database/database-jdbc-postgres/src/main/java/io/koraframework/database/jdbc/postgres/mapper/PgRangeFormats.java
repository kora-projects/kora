package io.koraframework.database.jdbc.postgres.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.Map;
import java.util.function.Function;

/**
 * Текстовое представление границ временных range-типов: PostgreSQL отдаёт их не в ISO-8601, а через пробел
 * ({@code 2021-01-01 10:00:00+03}), год без знака с суффиксом {@code BC} до нашей эры и больше 4 цифр после 9999,
 * а бесконечные границы как {@code infinity}/{@code -infinity}, поэтому стандартные {@code DateTimeFormatter} не подходят.
 */
final class PgRangeFormats {

    private static final String INFINITY = "infinity";
    private static final String NEGATIVE_INFINITY = "-infinity";

    private static final DateTimeFormatter DATE = formatter(false, false);
    private static final DateTimeFormatter TIMESTAMP = formatter(true, false);
    private static final DateTimeFormatter TIMESTAMP_WITH_TIMEZONE = formatter(true, true);

    static final Function<String, LocalDate> DATE_READER = reader(DATE, LocalDate::from, LocalDate.MIN, LocalDate.MAX);
    static final Function<LocalDate, String> DATE_WRITER = writer(DATE, LocalDate.MIN, LocalDate.MAX);

    static final Function<String, LocalDateTime> TIMESTAMP_READER = reader(TIMESTAMP, LocalDateTime::from, LocalDateTime.MIN, LocalDateTime.MAX);
    static final Function<LocalDateTime, String> TIMESTAMP_WRITER = writer(TIMESTAMP, LocalDateTime.MIN, LocalDateTime.MAX);

    static final Function<String, OffsetDateTime> TIMESTAMP_WITH_TIMEZONE_READER = reader(TIMESTAMP_WITH_TIMEZONE, OffsetDateTime::from, OffsetDateTime.MIN, OffsetDateTime.MAX);
    static final Function<OffsetDateTime, String> TIMESTAMP_WITH_TIMEZONE_WRITER = writer(TIMESTAMP_WITH_TIMEZONE, OffsetDateTime.MIN, OffsetDateTime.MAX);

    private PgRangeFormats() { }

    private static DateTimeFormatter formatter(boolean withTime, boolean withOffset) {
        var builder = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR_OF_ERA, 4, 10, SignStyle.NOT_NEGATIVE)
            .appendPattern("-MM-dd");
        if (withTime) {
            builder.appendPattern(" HH:mm:ss").appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true);
        }
        if (withOffset) {
            // "+HH:mm:ss" печатает и разбирает "+03", "+05:30" и "+00:19:32" (исторические offset) — ровно то, что отдаёт PostgreSQL
            builder.appendOffset("+HH:mm:ss", "+00");
        }
        // PostgreSQL пишет эру после offset: "0044-03-15 00:00:00+00 BC"
        return builder.appendText(ChronoField.ERA, Map.of(0L, " BC", 1L, "")).toFormatter();
    }

    // бесконечные границы PostgreSQL соответствуют MIN/MAX, как у pgjdbc для обычных date/timestamp колонок
    private static <T extends TemporalAccessor> Function<String, T> reader(DateTimeFormatter formatter, TemporalQuery<T> query, T min, T max) {
        return text -> switch (text) {
            case INFINITY -> max;
            case NEGATIVE_INFINITY -> min;
            default -> formatter.parse(text, query);
        };
    }

    private static <T extends TemporalAccessor> Function<T, String> writer(DateTimeFormatter formatter, T min, T max) {
        return value -> value.equals(max) ? INFINITY
            : value.equals(min) ? NEGATIVE_INFINITY
            : formatter.format(value);
    }
}

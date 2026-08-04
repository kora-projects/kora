package io.koraframework.database.jdbc.postgres;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * Текстовое представление границ временных range-типов: PostgreSQL отдаёт их не в ISO-8601, а через пробел
 * ({@code 2021-01-01 10:00:00+03}), поэтому стандартные {@code DateTimeFormatter} не подходят.
 */
final class PgRangeFormats {

    static final DateTimeFormatter TIMESTAMP = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .toFormatter();

    // "+HH:mm" печатает и разбирает как "+03", так и "+05:30" — ровно то, что отдаёт PostgreSQL
    static final DateTimeFormatter TIMESTAMP_WITH_TIMEZONE = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .appendOffset("+HH:mm", "+00")
        .toFormatter();

    private PgRangeFormats() { }
}

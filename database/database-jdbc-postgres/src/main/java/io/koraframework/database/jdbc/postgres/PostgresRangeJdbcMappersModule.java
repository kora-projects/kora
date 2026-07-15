package io.koraframework.database.jdbc.postgres;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.parameter.RangeParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.result.RangeResultColumnMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

public interface PostgresRangeJdbcMappersModule {

    DateTimeFormatter TS_FORMAT = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
        .toFormatter();

    DateTimeFormatter TSTZ_FORMAT = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
        .appendOffset("+HH:mm", "+00")
        .toFormatter();

    @DefaultComponent
    default PostgresRangeColumnData<Integer> int4RangeColumnData() {
        return new PostgresRangeColumnData<>("int4range", String::valueOf, Integer::valueOf);
    }

    @DefaultComponent
    default PostgresRangeColumnData<Long> int8RangeColumnData() {
        return new PostgresRangeColumnData<>("int8range", String::valueOf, Long::valueOf);
    }

    @DefaultComponent
    default PostgresRangeColumnData<BigDecimal> numRangeColumnData() {
        return new PostgresRangeColumnData<>("numrange", BigDecimal::toPlainString, BigDecimal::new);
    }

    @DefaultComponent
    default PostgresRangeColumnData<LocalDate> dateRangeColumnData() {
        return new PostgresRangeColumnData<>("daterange", LocalDate::toString, LocalDate::parse);
    }

    @DefaultComponent
    default PostgresRangeColumnData<LocalDateTime> tsRangeColumnData() {
        return new PostgresRangeColumnData<>("tsrange", TS_FORMAT::format, s -> LocalDateTime.parse(s, TS_FORMAT));
    }

    @DefaultComponent
    default PostgresRangeColumnData<OffsetDateTime> tstzRangeColumnData() {
        return new PostgresRangeColumnData<>("tstzrange", TSTZ_FORMAT::format, s -> OffsetDateTime.parse(s, TSTZ_FORMAT));
    }

    default <T> JdbcParameterColumnMapper<Range<T>> rangeJdbcParameterColumnMapper(PostgresRangeColumnData<T> rangeColumnData) {
        return new RangeParameterColumnMapper<>(rangeColumnData);
    }

    default <T> JdbcResultColumnMapper<Range<T>> rangeJdbcResultColumnMapper(PostgresRangeColumnData<T> rangeColumnData) {
        return new RangeResultColumnMapper<>(rangeColumnData);
    }
}

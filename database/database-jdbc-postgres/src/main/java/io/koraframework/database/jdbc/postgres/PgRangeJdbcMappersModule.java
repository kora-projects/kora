package io.koraframework.database.jdbc.postgres;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.database.jdbc.mapper.parameter.JdbcParameterColumnMapper;
import io.koraframework.database.jdbc.mapper.result.JdbcResultColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.parameter.PgRangeParameterColumnMapper;
import io.koraframework.database.jdbc.postgres.mapper.result.PgRangeResultColumnMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * <b>Русский</b>: Конвертеры {@link PgRange} в range-типы PostgreSQL. Тега не требуют, потому что сам
 * {@link PgRange} уже PostgreSQL специфичен и других претендентов на эти конвертеры нет.
 * <hr>
 * <b>English</b>: Converters of {@link PgRange} into PostgreSQL range types. They need no tag, because {@link PgRange}
 * itself is already PostgreSQL specific and nothing else can claim those converters.
 */
public interface PgRangeJdbcMappersModule {

    @DefaultComponent
    default JdbcParameterColumnMapper<PgRange<Integer>> int4RangeJdbcParameterColumnMapper() {
        return new PgRangeParameterColumnMapper<>("int4range", String::valueOf);
    }

    @DefaultComponent
    default JdbcResultColumnMapper<PgRange<Integer>> int4RangeJdbcResultColumnMapper() {
        return new PgRangeResultColumnMapper<>(Integer::valueOf);
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<PgRange<Long>> int8RangeJdbcParameterColumnMapper() {
        return new PgRangeParameterColumnMapper<>("int8range", String::valueOf);
    }

    @DefaultComponent
    default JdbcResultColumnMapper<PgRange<Long>> int8RangeJdbcResultColumnMapper() {
        return new PgRangeResultColumnMapper<>(Long::valueOf);
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<PgRange<BigDecimal>> numRangeJdbcParameterColumnMapper() {
        return new PgRangeParameterColumnMapper<>("numrange", BigDecimal::toPlainString);
    }

    @DefaultComponent
    default JdbcResultColumnMapper<PgRange<BigDecimal>> numRangeJdbcResultColumnMapper() {
        return new PgRangeResultColumnMapper<>(BigDecimal::new);
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<PgRange<LocalDate>> dateRangeJdbcParameterColumnMapper() {
        return new PgRangeParameterColumnMapper<>("daterange", LocalDate::toString);
    }

    @DefaultComponent
    default JdbcResultColumnMapper<PgRange<LocalDate>> dateRangeJdbcResultColumnMapper() {
        return new PgRangeResultColumnMapper<>(LocalDate::parse);
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<PgRange<LocalDateTime>> tsRangeJdbcParameterColumnMapper() {
        return new PgRangeParameterColumnMapper<>("tsrange", PgRangeFormats.TIMESTAMP::format);
    }

    @DefaultComponent
    default JdbcResultColumnMapper<PgRange<LocalDateTime>> tsRangeJdbcResultColumnMapper() {
        return new PgRangeResultColumnMapper<>(bound -> LocalDateTime.parse(bound, PgRangeFormats.TIMESTAMP));
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<PgRange<OffsetDateTime>> tstzRangeJdbcParameterColumnMapper() {
        return new PgRangeParameterColumnMapper<>("tstzrange", PgRangeFormats.TIMESTAMP_WITH_TIMEZONE::format);
    }

    @DefaultComponent
    default JdbcResultColumnMapper<PgRange<OffsetDateTime>> tstzRangeJdbcResultColumnMapper() {
        return new PgRangeResultColumnMapper<>(bound -> OffsetDateTime.parse(bound, PgRangeFormats.TIMESTAMP_WITH_TIMEZONE));
    }
}

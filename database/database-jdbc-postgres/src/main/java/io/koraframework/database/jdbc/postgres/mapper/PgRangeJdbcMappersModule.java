package io.koraframework.database.jdbc.postgres.mapper;

import io.koraframework.common.annotation.*;
import io.koraframework.database.jdbc.mapper.parameter.*;
import io.koraframework.database.jdbc.mapper.result.*;
import io.koraframework.database.jdbc.postgres.*;
import io.koraframework.database.jdbc.postgres.mapper.parameter.*;
import io.koraframework.database.jdbc.postgres.mapper.result.*;

import java.math.*;
import java.time.*;
import java.util.function.*;

/**
 * <b>Русский</b>: Конвертеры {@link PgRange} в range-типы PostgreSQL. Тега не требуют, потому что сам
 * {@link PgRange} уже PostgreSQL специфичен и других претендентов на эти конвертеры нет.
 * <hr>
 * <b>English</b>: Converters of {@link PgRange} into PostgreSQL range types. They need no tag, because {@link PgRange}
 * itself is already PostgreSQL specific and nothing else can claim those converters.
 */
public interface PgRangeJdbcMappersModule {

    @DefaultComponent
    default JdbcParameterColumnMapper<PgRange<Integer>> int4RangePostgresJdbcParameterColumnMapper() {
        return Mappers.INT4_PARAM;
    }

    @DefaultComponent
    default JdbcResultColumnMapper<PgRange<Integer>> int4RangePostgresJdbcResultColumnMapper() {
        return Mappers.INT4_RESULT;
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<PgRange<Long>> int8RangePostgresJdbcParameterColumnMapper() {
        return Mappers.INT8_PARAM;
    }

    @DefaultComponent
    default JdbcResultColumnMapper<PgRange<Long>> int8RangePostgresJdbcResultColumnMapper() {
        return Mappers.INT8_RESULT;
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<PgRange<BigDecimal>> numRangePostgresJdbcParameterColumnMapper() {
        return Mappers.NUM_PARAM;
    }

    @DefaultComponent
    default JdbcResultColumnMapper<PgRange<BigDecimal>> numRangePostgresJdbcResultColumnMapper() {
        return Mappers.NUM_RESULT;
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<PgRange<LocalDate>> dateRangePostgresJdbcParameterColumnMapper() {
        return Mappers.DATE_PARAM;
    }

    @DefaultComponent
    default JdbcResultColumnMapper<PgRange<LocalDate>> dateRangePostgresJdbcResultColumnMapper() {
        return Mappers.DATE_RESULT;
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<PgRange<LocalDateTime>> tsRangePostgresJdbcParameterColumnMapper() {
        return Mappers.TS_PARAM;
    }

    @DefaultComponent
    default JdbcResultColumnMapper<PgRange<LocalDateTime>> tsRangePostgresJdbcResultColumnMapper() {
        return Mappers.TS_RESULT;
    }

    @DefaultComponent
    default JdbcParameterColumnMapper<PgRange<OffsetDateTime>> tstzRangePostgresJdbcParameterColumnMapper() {
        return Mappers.TSTZ_PARAM;
    }

    @DefaultComponent
    default JdbcResultColumnMapper<PgRange<OffsetDateTime>> tstzRangePostgresJdbcResultColumnMapper() {
        return Mappers.TSTZ_RESULT;
    }

    class Functions {
        public static final StringValueOf STRING_VALUE_OF = new StringValueOf();
        public static final IntegerValueOf INTEGER_VALUE_OF = new IntegerValueOf();
        public static final LongValueOf LONG_VALUE_OF = new LongValueOf();
        public static final BigDecimalToPlainString BIG_DECIMAL_TO_PLAIN = new BigDecimalToPlainString();
        public static final BigDecimalNew BIG_DECIMAL_NEW = new BigDecimalNew();
        public static final LocalDateToString LOCAL_DATE_TO_STRING = new LocalDateToString();
        public static final LocalDateParse LOCAL_DATE_PARSE = new LocalDateParse();
        public static final LocalDateTimeFormat LOCAL_DATE_TIME_FORMAT = new LocalDateTimeFormat();
        public static final LocalDateTimeParse LOCAL_DATE_TIME_PARSE = new LocalDateTimeParse();
        public static final OffsetDateTimeFormat OFFSET_DATE_TIME_FORMAT = new OffsetDateTimeFormat();
        public static final OffsetDateTimeParse OFFSET_DATE_TIME_PARSE = new OffsetDateTimeParse();

        public static final class StringValueOf implements Function<Object, String> {
            @Override
            public String apply(Object t) {return String.valueOf(t);}
        }

        public static final class IntegerValueOf implements Function<String, Integer> {
            @Override
            public Integer apply(String s) {return Integer.valueOf(s);}
        }

        public static final class LongValueOf implements Function<String, Long> {
            @Override
            public Long apply(String s) {return Long.valueOf(s);}
        }

        public static final class BigDecimalToPlainString implements Function<BigDecimal, String> {
            @Override
            public String apply(BigDecimal b) {return b.toPlainString();}
        }

        public static final class BigDecimalNew implements Function<String, BigDecimal> {
            @Override
            public BigDecimal apply(String s) {return new BigDecimal(s);}
        }

        public static final class LocalDateToString implements Function<LocalDate, String> {
            @Override
            public String apply(LocalDate d) {return d.toString();}
        }

        public static final class LocalDateParse implements Function<String, LocalDate> {
            @Override
            public LocalDate apply(String s) {return LocalDate.parse(s);}
        }

        public static final class LocalDateTimeFormat implements Function<LocalDateTime, String> {
            @Override
            public String apply(LocalDateTime dt) {return PgRangeFormats.TIMESTAMP.format(dt);}
        }

        public static final class LocalDateTimeParse implements Function<String, LocalDateTime> {
            @Override
            public LocalDateTime apply(String s) {return LocalDateTime.parse(s, PgRangeFormats.TIMESTAMP);}
        }

        public static final class OffsetDateTimeFormat implements Function<OffsetDateTime, String> {
            @Override
            public String apply(OffsetDateTime odt) {return PgRangeFormats.TIMESTAMP_WITH_TIMEZONE.format(odt);}
        }

        public static final class OffsetDateTimeParse implements Function<String, OffsetDateTime> {
            @Override
            public OffsetDateTime apply(String s) {return OffsetDateTime.parse(s, PgRangeFormats.TIMESTAMP_WITH_TIMEZONE);}
        }
    }

    class Mappers {
        @SuppressWarnings("unchecked")
        public static final JdbcParameterColumnMapper<PgRange<Integer>> INT4_PARAM =
            new PgRangeParameterColumnMapper<>("int4range", (Function<Integer, String>) (Function<?, ?>) Functions.STRING_VALUE_OF);
        public static final JdbcResultColumnMapper<PgRange<Integer>> INT4_RESULT =
            new PgRangeResultColumnMapper<>(Functions.INTEGER_VALUE_OF);

        @SuppressWarnings("unchecked")
        public static final JdbcParameterColumnMapper<PgRange<Long>> INT8_PARAM =
            new PgRangeParameterColumnMapper<>("int8range", (Function<Long, String>) (Function<?, ?>) Functions.STRING_VALUE_OF);
        public static final JdbcResultColumnMapper<PgRange<Long>> INT8_RESULT =
            new PgRangeResultColumnMapper<>(Functions.LONG_VALUE_OF);

        public static final JdbcParameterColumnMapper<PgRange<BigDecimal>> NUM_PARAM =
            new PgRangeParameterColumnMapper<>("numrange", Functions.BIG_DECIMAL_TO_PLAIN);
        public static final JdbcResultColumnMapper<PgRange<BigDecimal>> NUM_RESULT =
            new PgRangeResultColumnMapper<>(Functions.BIG_DECIMAL_NEW);

        public static final JdbcParameterColumnMapper<PgRange<LocalDate>> DATE_PARAM =
            new PgRangeParameterColumnMapper<>("daterange", Functions.LOCAL_DATE_TO_STRING);
        public static final JdbcResultColumnMapper<PgRange<LocalDate>> DATE_RESULT =
            new PgRangeResultColumnMapper<>(Functions.LOCAL_DATE_PARSE);

        public static final JdbcParameterColumnMapper<PgRange<LocalDateTime>> TS_PARAM =
            new PgRangeParameterColumnMapper<>("tsrange", Functions.LOCAL_DATE_TIME_FORMAT);
        public static final JdbcResultColumnMapper<PgRange<LocalDateTime>> TS_RESULT =
            new PgRangeResultColumnMapper<>(Functions.LOCAL_DATE_TIME_PARSE);

        public static final JdbcParameterColumnMapper<PgRange<OffsetDateTime>> TSTZ_PARAM =
            new PgRangeParameterColumnMapper<>("tstzrange", Functions.OFFSET_DATE_TIME_FORMAT);
        public static final JdbcResultColumnMapper<PgRange<OffsetDateTime>> TSTZ_RESULT =
            new PgRangeResultColumnMapper<>(Functions.OFFSET_DATE_TIME_PARSE);
    }
}

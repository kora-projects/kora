package io.koraframework.http.server.common.request.mapper;

import io.koraframework.application.graph.*;
import io.koraframework.common.annotation.*;
import io.koraframework.http.server.common.request.*;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.annotation.*;

import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.*;

public interface HttpServerParameterReaderModule {

    @DefaultComponent
    default <T extends Enum<T>> HttpServerParameterReader<T> enumHttpServerParameterReader(TypeRef<T> typeRef) {
        return new EnumHttpServerParameterReader<>(typeRef.getRawType().getEnumConstants(), new EnumNameFunction<>());
    }

    @DefaultComponent
    default HttpServerParameterReader<OffsetTime> offsetTimeHttpServerParameterReader() {
        return HttpServerParameterReader.of(new OffsetTimeParser(), new OffsetTimeErrorFormatter());
    }

    @DefaultComponent
    default HttpServerParameterReader<OffsetDateTime> offsetDateTimeHttpServerParameterReader() {
        return HttpServerParameterReader.of(new OffsetDateTimeParser(), new OffsetDateTimeErrorFormatter());
    }

    @DefaultComponent
    default HttpServerParameterReader<LocalTime> localTimeHttpServerParameterReader() {
        return HttpServerParameterReader.of(new LocalTimeParser(), new LocalTimeErrorFormatter());
    }

    @DefaultComponent
    default HttpServerParameterReader<LocalDateTime> localDateTimeHttpServerParameterReader() {
        return HttpServerParameterReader.of(new LocalDateTimeParser(), new LocalDateTimeErrorFormatter());
    }

    @DefaultComponent
    default HttpServerParameterReader<LocalDate> localDateHttpServerParameterReader() {
        return HttpServerParameterReader.of(new LocalDateParser(), new LocalDateErrorFormatter());
    }

    @DefaultComponent
    default HttpServerParameterReader<ZonedDateTime> zonedDateTimeHttpServerParameterReader() {
        return HttpServerParameterReader.of(new ZonedDateTimeParser(), new ZonedDateTimeErrorFormatter());
    }

    @DefaultComponent
    default HttpServerParameterReader<Boolean> booleanHttpServerParameterReader() {
        return HttpServerParameterReader.of(new BooleanParser(), new BooleanErrorFormatter());
    }

    private static boolean parseBooleanStrict(String value) {
        if ("true".equals(value)) {
            return true;
        } else if ("false".equals(value)) {
            return false;
        } else {
            throw new IllegalArgumentException("Invalid boolean value: " + value);
        }
    }

    @DefaultComponent
    default HttpServerParameterReader<Integer> integerHttpServerParameterReader() {
        return HttpServerParameterReader.of(new IntegerParser(), new IntegerErrorFormatter());
    }

    @DefaultComponent
    default HttpServerParameterReader<Long> longHttpServerParameterReader() {
        return HttpServerParameterReader.of(new LongParser(), new LongErrorFormatter());
    }

    @DefaultComponent
    default HttpServerParameterReader<Float> floatHttpServerParameterReader() {
        return HttpServerParameterReader.of(new FloatParser(), new FloatErrorFormatter());
    }

    @DefaultComponent
    default HttpServerParameterReader<Double> doubleHttpServerParameterReader() {
        return HttpServerParameterReader.of(new DoubleParser(), new DoubleErrorFormatter());
    }

    @DefaultComponent
    default HttpServerParameterReader<UUID> uuidHttpServerParameterReader() {
        return HttpServerParameterReader.of(new UuidParser(), new UuidErrorFormatter());
    }

    @DefaultComponent
    default HttpServerParameterReader<BigInteger> bigIntegerHttpServerParameterReader() {
        return HttpServerParameterReader.of(new BigIntegerParser(), new BigIntegerErrorFormatter());
    }

    @DefaultComponent
    default HttpServerParameterReader<BigDecimal> bigDecimalHttpServerParameterReader() {
        return HttpServerParameterReader.of(new BigDecimalParser(), new BigDecimalErrorFormatter());
    }

    @DefaultComponent
    default HttpServerParameterReader<Duration> durationHttpServerParameterReader() {
        return HttpServerParameterReader.of(new DurationParser(), new DurationErrorFormatter());
    }

    @Json
    @DefaultComponent
    default <T> HttpServerParameterReader<T> jsonHttpServerParameterReader(JsonReader<T> reader) {
        return new JsonHttpServerParameterReader<>(reader);
    }

    final class EnumNameFunction<T extends Enum<T>> implements Function<T, String> {
        @Override
        public String apply(T enumValue) {
            return enumValue.name();
        }
    }

    final class OffsetTimeParser implements Function<String, OffsetTime> {
        @Override
        public OffsetTime apply(String s) {
            return OffsetTime.parse(s);
        }
    }

    final class OffsetTimeErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "', expected format is '10:15:30+01:00'";
        }
    }

    final class OffsetDateTimeParser implements Function<String, OffsetDateTime> {
        @Override
        public OffsetDateTime apply(String s) {
            return OffsetDateTime.parse(s);
        }
    }

    final class OffsetDateTimeErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "', expected format is '2007-12-03T10:15:30+01:00'";
        }
    }

    final class LocalTimeParser implements Function<String, LocalTime> {
        @Override
        public LocalTime apply(String s) {
            return LocalTime.parse(s);
        }
    }

    final class LocalTimeErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "', expected format is '10:15'";
        }
    }

    final class LocalDateTimeParser implements Function<String, LocalDateTime> {
        @Override
        public LocalDateTime apply(String s) {
            return LocalDateTime.parse(s);
        }
    }

    final class LocalDateTimeErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "', expected format is '2007-12-03T10:15:30'";
        }
    }

    final class LocalDateParser implements Function<String, LocalDate> {
        @Override
        public LocalDate apply(String s) {
            return LocalDate.parse(s);
        }
    }

    final class LocalDateErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "', expected format is '2007-12-03'";
        }
    }

    final class ZonedDateTimeParser implements Function<String, ZonedDateTime> {
        @Override
        public ZonedDateTime apply(String s) {
            return ZonedDateTime.parse(s);
        }
    }

    final class ZonedDateTimeErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "', expected format is '2007-12-03T10:15:30+01:00[Europe/Paris]'";
        }
    }

    final class BooleanParser implements Function<String, Boolean> {
        @Override
        public Boolean apply(String s) {
            return HttpServerParameterReaderModule.parseBooleanStrict(s);
        }
    }

    final class BooleanErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "' for 'Boolean' type";
        }
    }

    final class IntegerParser implements Function<String, Integer> {
        @Override
        public Integer apply(String s) {
            return Integer.parseInt(s);
        }
    }

    final class IntegerErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "' for 'Integer' type";
        }
    }

    final class LongParser implements Function<String, Long> {
        @Override
        public Long apply(String s) {
            return Long.parseLong(s);
        }
    }

    final class LongErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "' for 'Long' type";
        }
    }

    final class FloatParser implements Function<String, Float> {
        @Override
        public Float apply(String s) {
            return Float.parseFloat(s);
        }
    }

    final class FloatErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "' for 'Float' type";
        }
    }

    final class DoubleParser implements Function<String, Double> {
        @Override
        public Double apply(String s) {
            return Double.parseDouble(s);
        }
    }

    final class DoubleErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "' for 'Double' type";
        }
    }

    final class UuidParser implements Function<String, UUID> {
        @Override
        public UUID apply(String s) {
            return UUID.fromString(s);
        }
    }

    final class UuidErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "' for 'UUID' type";
        }
    }

    final class BigIntegerParser implements Function<String, BigInteger> {
        @Override
        public BigInteger apply(String s) {
            return new BigInteger(s);
        }
    }

    final class BigIntegerErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "' for 'BigInteger' type";
        }
    }

    final class BigDecimalParser implements Function<String, BigDecimal> {
        @Override
        public BigDecimal apply(String s) {
            return new BigDecimal(s);
        }
    }

    final class BigDecimalErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "' for 'BigDecimal' type";
        }
    }

    final class DurationParser implements Function<String, Duration> {
        @Override
        public Duration apply(String s) {
            return Duration.parse(s);
        }
    }

    final class DurationErrorFormatter implements Function<String, String> {
        @Override
        public String apply(String s) {
            return "Parameter has incorrect value '" + s + "' for 'Duration' type";
        }
    }
}

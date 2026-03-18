package io.koraframework.http.server.common.request.mapper;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.http.server.common.request.HttpServerParameterReader;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.annotation.Json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.UUID;

public interface HttpParameterReaderModule {

    @DefaultComponent
    default <T extends Enum<T>> HttpServerParameterReader<T> enumStringParameterReader(TypeRef<T> typeRef) {
        return new EnumHttpServerParameterReader<>(typeRef.getRawType().getEnumConstants(), Enum::name);
    }

    @DefaultComponent
    default HttpServerParameterReader<OffsetTime> javaTimeOffsetTimeStringParameterReader() {
        return HttpServerParameterReader.of(java.time.OffsetTime::parse, "Parameter has incorrect value '%s', expected format is '10:15:30+01:00'"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<OffsetDateTime> javaTimeOffsetDateTimeStringParameterReader() {
        return HttpServerParameterReader.of(java.time.OffsetDateTime::parse, "Parameter has incorrect value '%s'', expected format is '2007-12-03T10:15:30+01:00'"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<LocalTime> javaTimeLocalTimeStringParameterReader() {
        return HttpServerParameterReader.of(java.time.LocalTime::parse, "Parameter has incorrect value '%s'', expected format is '10:15'"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<LocalDateTime> javaTimeLocalDateTimeStringParameterReader() {
        return HttpServerParameterReader.of(java.time.LocalDateTime::parse, "Parameter has incorrect value '%s'', expected format is '2007-12-03T10:15:30'"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<LocalDate> javaTimeLocalDateStringParameterReader() {
        return HttpServerParameterReader.of(java.time.LocalDate::parse, "Parameter has incorrect value '%s'', expected format is '2007-12-03'"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<ZonedDateTime> javaTimeZonedDateTimeStringParameterReader() {
        return HttpServerParameterReader.of(java.time.ZonedDateTime::parse, "Parameter has incorrect value '%s'', expected format is '2007-12-03T10:15:30+01:00[Europe/Paris]'"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<Boolean> javaUtilBooleanStringParameterReader() {
        return HttpServerParameterReader.of(Boolean::parseBoolean, "Parameter has incorrect value '%s' for 'Boolean' type"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<Integer> javaUtilIntegerStringParameterReader() {
        return HttpServerParameterReader.of(Integer::parseInt, "Parameter has incorrect value '%s' for 'Integer' type"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<Long> javaUtilLongStringParameterReader() {
        return HttpServerParameterReader.of(Long::parseLong, "Parameter has incorrect value '%s' for 'Long' type"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<Float> javaUtilFloatStringParameterReader() {
        return HttpServerParameterReader.of(Float::parseFloat, "Parameter has incorrect value '%s' for 'Float' type"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<Double> javaUtilDoubleStringParameterReader() {
        return HttpServerParameterReader.of(Double::parseDouble, "Parameter has incorrect value '%s' for 'Double' type"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<UUID> javaUtilUUIDStringParameterReader() {
        return HttpServerParameterReader.of(UUID::fromString, "Parameter has incorrect value '%s' for 'UUID' type"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<BigInteger> javaBigIntegerStringParameterReader() {
        return HttpServerParameterReader.of(BigInteger::new, "Parameter has incorrect value '%s' for 'BigInteger' type"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<BigDecimal> javaBigDecimalStringParameterReader() {
        return HttpServerParameterReader.of(BigDecimal::new, "Parameter has incorrect value '%s' for 'BigDecimal' type"::formatted);
    }

    @DefaultComponent
    default HttpServerParameterReader<Duration> javaDurationStringParameterReader() {
        return HttpServerParameterReader.of(Duration::parse, "Parameter has incorrect value '%s' for 'Duration' type"::formatted);
    }

    @DefaultComponent
    @Tag(Json.class)
    default <T> JsonHttpServerParameterReader<T> jsonStringParameterReader(JsonReader<T> reader) {
        return new JsonHttpServerParameterReader<>(reader);
    }
}

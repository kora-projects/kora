package io.koraframework.http.client.common.response.mapper;

import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.http.client.common.response.HttpClientParameterWriter;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.annotation.Json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public interface HttpClientParameterWriterModule {

    @DefaultComponent
    default HttpClientParameterWriter<Boolean> booleanConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Short> shortConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Integer> integerConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Long> longConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Double> doubleConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Float> floatConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<UUID> uuidConverter() {
        return UUID::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<BigDecimal> bigDecimalConverter() {
        return BigDecimal::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<BigInteger> bigIntegerConverter() {
        return BigInteger::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Duration> durationConverter() {
        return Duration::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<OffsetTime> javaTimeOffsetTimeStringParameterConverter() {
        return DateTimeFormatter.ISO_OFFSET_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<OffsetDateTime> javaTimeOffsetDateTimeStringParameterConverter() {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<LocalTime> javaTimeLocalTimeStringParameterConverter() {
        return DateTimeFormatter.ISO_LOCAL_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<LocalDate> javaTimeLocalDateStringParameterConverter() {
        return DateTimeFormatter.ISO_LOCAL_DATE::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<LocalDateTime> javaTimeLocalDateTimeStringParameterConverter() {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<ZonedDateTime> javaTimeZonedDateTimeStringParameterConverter() {
        return DateTimeFormatter.ISO_ZONED_DATE_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Instant> javaTimeInstantStringParameterConverter() {
        return DateTimeFormatter.ISO_INSTANT::format;
    }

    @DefaultComponent
    @Tag(Json.class)
    default <T> JsonHttpClientParameterWriter<T> jsonStringParameterConverter(JsonWriter<T> writer) {
        return new JsonHttpClientParameterWriter<>(writer);
    }
}

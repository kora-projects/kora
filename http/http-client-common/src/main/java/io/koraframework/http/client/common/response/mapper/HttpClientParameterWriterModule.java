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
    default HttpClientParameterWriter<Boolean> booleanHttpClientParameterWriter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Short> shortHttpClientParameterWriter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Integer> integerHttpClientParameterWriter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Long> longHttpClientParameterWriter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Double> doubleHttpClientParameterWriter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Float> floatHttpClientParameterWriter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<UUID> uuidHttpClientParameterWriter() {
        return UUID::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<BigDecimal> bigDecimalHttpClientParameterWriter() {
        return BigDecimal::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<BigInteger> bigIntegerHttpClientParameterWriter() {
        return BigInteger::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Duration> durationHttpClientParameterWriter() {
        return Duration::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<OffsetTime> javaTimeOffsetTimeHttpClientParameterWriter() {
        return DateTimeFormatter.ISO_OFFSET_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<OffsetDateTime> javaTimeOffsetDateTimeHttpClientParameterWriter() {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<LocalTime> javaTimeLocalTimeHttpClientParameterWriter() {
        return DateTimeFormatter.ISO_LOCAL_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<LocalDate> javaTimeLocalDateHttpClientParameterWriter() {
        return DateTimeFormatter.ISO_LOCAL_DATE::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<LocalDateTime> javaTimeLocalDateTimeHttpClientParameterWriter() {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<ZonedDateTime> javaTimeZonedDateTimeHttpClientParameterWriter() {
        return DateTimeFormatter.ISO_ZONED_DATE_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Instant> javaTimeInstantHttpClientParameterWriter() {
        return DateTimeFormatter.ISO_INSTANT::format;
    }

    @DefaultComponent
    @Tag(Json.class)
    default <T> JsonHttpClientParameterWriter<T> jsonHttpClientParameterWriter(JsonWriter<T> writer) {
        return new JsonHttpClientParameterWriter<>(writer);
    }
}

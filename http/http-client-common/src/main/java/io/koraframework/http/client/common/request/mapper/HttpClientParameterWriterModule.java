package io.koraframework.http.client.common.request.mapper;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.http.client.common.request.HttpClientParameterWriter;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.annotation.Json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public interface HttpClientParameterWriterModule {

    @DefaultComponent
    default HttpClientParameterWriter<Boolean> httpClientParameterBooleanWriter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Short> httpClientParameterShortWriter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Integer> httpClientParameterIntegerWriter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Long> httpClientParameterLongWriter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Double> httpClientParameterDoubleWriter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Float> httpClientParameterFloatWriter() {
        return Object::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<UUID> httpClientParameterUuidWriter() {
        return UUID::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<BigDecimal> httpClientParameterBigDecimalWriter() {
        return BigDecimal::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<BigInteger> httpClientParameterBigIntegerWriter() {
        return BigInteger::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Duration> httpClientParameterDurationWriter() {
        return Duration::toString;
    }

    @DefaultComponent
    default HttpClientParameterWriter<OffsetTime> httpClientParameterJavaTimeOffsetTimeWriter() {
        return DateTimeFormatter.ISO_OFFSET_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<OffsetDateTime> httpClientParameterJavaTimeOffsetDateTimeWriter() {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<LocalTime> httpClientParameterJavaTimeLocalTimeWriter() {
        return DateTimeFormatter.ISO_LOCAL_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<LocalDate> httpClientParameterJavaTimeLocalDateWriter() {
        return DateTimeFormatter.ISO_LOCAL_DATE::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<LocalDateTime> httpClientParameterJavaTimeLocalDateTimeWriter() {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<ZonedDateTime> httpClientParameterJavaTimeZonedDateTimeWriter() {
        return DateTimeFormatter.ISO_ZONED_DATE_TIME::format;
    }

    @DefaultComponent
    default HttpClientParameterWriter<Instant> httpClientParameterJavaTimeInstantWriter() {
        return DateTimeFormatter.ISO_INSTANT::format;
    }

    @Json
    @DefaultComponent
    default <T> HttpClientParameterWriter<T> httpClientParameterJsonWriter(JsonWriter<T> jsonWriter) {
        return new JsonHttpClientParameterWriter<>(jsonWriter);
    }
}

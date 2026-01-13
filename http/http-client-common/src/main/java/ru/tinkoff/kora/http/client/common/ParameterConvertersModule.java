package ru.tinkoff.kora.http.client.common;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.http.client.common.request.mapper.JsonStringParameterConverter;
import ru.tinkoff.kora.http.client.common.writer.StringParameterConverter;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.common.annotation.Json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public interface ParameterConvertersModule {

    @DefaultComponent
    default StringParameterConverter<Boolean> booleanConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default StringParameterConverter<Short> shortConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default StringParameterConverter<Integer> integerConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default StringParameterConverter<Long> longConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default StringParameterConverter<Double> doubleConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default StringParameterConverter<Float> floatConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default StringParameterConverter<UUID> uuidConverter() {
        return UUID::toString;
    }

    @DefaultComponent
    default StringParameterConverter<BigDecimal> bigDecimalConverter() {
        return BigDecimal::toString;
    }

    @DefaultComponent
    default StringParameterConverter<BigInteger> bigIntegerConverter() {
        return BigInteger::toString;
    }

    @DefaultComponent
    default StringParameterConverter<Duration> durationConverter() {
        return Duration::toString;
    }

    @DefaultComponent
    default StringParameterConverter<OffsetTime> javaTimeOffsetTimeStringParameterConverter() {
        return DateTimeFormatter.ISO_OFFSET_TIME::format;
    }

    @DefaultComponent
    default StringParameterConverter<OffsetDateTime> javaTimeOffsetDateTimeStringParameterConverter() {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME::format;
    }

    @DefaultComponent
    default StringParameterConverter<LocalTime> javaTimeLocalTimeStringParameterConverter() {
        return DateTimeFormatter.ISO_LOCAL_TIME::format;
    }

    @DefaultComponent
    default StringParameterConverter<LocalDate> javaTimeLocalDateStringParameterConverter() {
        return DateTimeFormatter.ISO_LOCAL_DATE::format;
    }

    @DefaultComponent
    default StringParameterConverter<LocalDateTime> javaTimeLocalDateTimeStringParameterConverter() {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME::format;
    }

    @DefaultComponent
    default StringParameterConverter<ZonedDateTime> javaTimeZonedDateTimeStringParameterConverter() {
        return DateTimeFormatter.ISO_ZONED_DATE_TIME::format;
    }

    @DefaultComponent
    default StringParameterConverter<Instant> javaTimeInstantStringParameterConverter() {
        return DateTimeFormatter.ISO_INSTANT::format;
    }

    @DefaultComponent
    @Tag(Json.class)
    default <T> JsonStringParameterConverter<T> jsonStringParameterConverter(JsonWriter<T> writer) {
        return new JsonStringParameterConverter<>(writer);
    }
}

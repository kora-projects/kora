package ru.tinkoff.kora.http.client.common;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.http.client.common.writer.StringParameterConverter;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

public interface ParameterConvertersModule {
    @DefaultComponent
    default StringParameterConverter<Integer> integerConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default StringParameterConverter<Double> doubleConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default StringParameterConverter<Long> longConverter() {
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
    default StringParameterConverter<Boolean> booleanConverter() {
        return Object::toString;
    }

    @DefaultComponent
    default StringParameterConverter<java.time.OffsetTime> javaTimeOffsetTimeStringParameterConverter() {return DateTimeFormatter.ISO_OFFSET_TIME::format;}

    @DefaultComponent
    default StringParameterConverter<java.time.OffsetDateTime> javaTimeOffsetDateTimeStringParameterConverter() {return DateTimeFormatter.ISO_OFFSET_DATE_TIME::format;}

    @DefaultComponent
    default StringParameterConverter<java.time.LocalTime> javaTimeLocalTimeStringParameterConverter() {return DateTimeFormatter.ISO_LOCAL_TIME::format;}

    @DefaultComponent
    default StringParameterConverter<java.time.LocalDateTime> javaTimeLocalDateTimeStringParameterConverter() {return DateTimeFormatter.ISO_LOCAL_DATE_TIME::format;}

    @DefaultComponent
    default StringParameterConverter<java.time.LocalDate> javaTimeLocalDateStringParameterConverter() {return DateTimeFormatter.ISO_LOCAL_DATE::format;}

    @DefaultComponent
    default StringParameterConverter<java.time.ZonedDateTime> javaTimeZonedDateTimeStringParameterConverter() {return DateTimeFormatter.ISO_ZONED_DATE_TIME::format;}
}

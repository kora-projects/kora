package ru.tinkoff.kora.http.server.common;

import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.http.server.common.handler.EnumStringParameterReader;
import ru.tinkoff.kora.http.server.common.handler.StringParameterReader;

import java.util.UUID;

public interface StringParameterReadersModule {
    @DefaultComponent
    default <T extends Enum<T>> StringParameterReader<T> enumStringParameterReader(TypeRef<T> typeRef) {
        return new EnumStringParameterReader<>(typeRef.getRawType().getEnumConstants(), Enum::name);
    }

    @DefaultComponent
    default StringParameterReader<java.time.OffsetTime> javaTimeOffsetTimeStringParameterReader() {
        return StringParameterReader.of(java.time.OffsetTime::parse, "Parameter has incorrect value '%s', expected format is '10:15:30+01:00'"::formatted);
    }

    @DefaultComponent
    default StringParameterReader<java.time.OffsetDateTime> javaTimeOffsetDateTimeStringParameterReader() {
        return StringParameterReader.of(java.time.OffsetDateTime::parse, "Parameter has incorrect value '%s'', expected format is '2007-12-03T10:15:30+01:00'"::formatted);
    }

    @DefaultComponent
    default StringParameterReader<java.time.LocalTime> javaTimeLocalTimeStringParameterReader() {
        return StringParameterReader.of(java.time.LocalTime::parse, "Parameter has incorrect value '%s'', expected format is '10:15'"::formatted);
    }

    @DefaultComponent
    default StringParameterReader<java.time.LocalDateTime> javaTimeLocalDateTimeStringParameterReader() {
        return StringParameterReader.of(java.time.LocalDateTime::parse, "Parameter has incorrect value '%s'', expected format is '2007-12-03T10:15:30'"::formatted);
    }

    @DefaultComponent
    default StringParameterReader<java.time.ZonedDateTime> javaTimeZonedDateTimeStringParameterReader() {
        return StringParameterReader.of(java.time.ZonedDateTime::parse, "Parameter has incorrect value '%s'', expected format is '2007-12-03T10:15:30+01:00[Europe/Paris]'"::formatted);
    }


    @DefaultComponent
    default StringParameterReader<Integer> javaUtilIntegerStringParameterReader(){
        return StringParameterReader.of(Integer::parseInt,"Parameter has incorrect value '%s' for 'Integer' type"::formatted);
    }

    @DefaultComponent
    default StringParameterReader<Double> javaUtilDoubleStringParameterReader(){
        return StringParameterReader.of(Double::parseDouble,"Parameter has incorrect value '%s' for 'Double' type"::formatted);
    }

    @DefaultComponent
    default StringParameterReader<Long> javaUtilLongStringParameterReader(){
        return StringParameterReader.of(Long::parseLong,"Parameter has incorrect value '%s' for 'Long' type"::formatted);
    }

    @DefaultComponent
    default StringParameterReader<Float> javaUtilFloatStringParameterReader(){
        return StringParameterReader.of(Float::parseFloat,"Parameter has incorrect value '%s' for 'Float' type"::formatted);
    }

    @DefaultComponent
    default StringParameterReader<UUID> javaUtilUUIDStringParameterReader(){
        return StringParameterReader.of(UUID::fromString,"Parameter has incorrect value '%s' for 'UUID' type"::formatted);
    }

    @DefaultComponent
    default StringParameterReader<Boolean> javaUtilBooleanStringParameterReader(){
        return StringParameterReader.of(Boolean::parseBoolean,"Parameter has incorrect value '%s' for 'Boolean' type"::formatted);
    }
}

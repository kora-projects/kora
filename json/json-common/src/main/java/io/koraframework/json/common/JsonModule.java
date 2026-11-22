package io.koraframework.json.common;

import io.koraframework.common.annotation.*;
import io.koraframework.json.common.fastjson2.reader.*;
import io.koraframework.json.common.fastjson2.writer.*;

import java.math.*;
import java.time.*;
import java.util.*;

public interface JsonModule {

    @DefaultComponent
    default JsonWriter<Object> objectJsonWriter() {
        return new ObjectJsonWriter();
    }

    @DefaultComponent
    default JsonReader<Object> objectJsonReader() {
        return new ObjectJsonReader();
    }

    @DefaultComponent
    default <T> JsonWriter<List<T>> listJsonWriter(JsonWriter<T> writer) {
        return new ListJsonWriter<>(writer);
    }

    @DefaultComponent
    default <T> JsonReader<List<T>> listJsonReader(JsonReader<T> reader) {
        return new ListJsonReader<>(reader);
    }

    @DefaultComponent
    default <T> JsonWriter<Map<String, T>> mapJsonWriter(JsonWriter<T> writer) {
        return new MapJsonWriter<>(writer);
    }

    @DefaultComponent
    default <T> JsonReader<Map<String, T>> mapJsonReader(JsonReader<T> reader) {
        return new MapJsonReader<>(reader);
    }

    @DefaultComponent
    default <T> JsonWriter<Set<T>> setJsonWriter(JsonWriter<T> writer) {
        return new SetJsonWriter<>(writer);
    }

    @DefaultComponent
    default <T> JsonReader<Set<T>> setJsonReader(JsonReader<T> reader) {
        return new SetJsonReader<>(reader);
    }

    @DefaultComponent
    default <T extends Comparable<T>> JsonReader<SortedSet<T>> sortedSetJsonReader(JsonReader<T> reader) {
        return new SortedSetJsonReader<>(reader);
    }

    @DefaultComponent
    default JsonWriter<Short> shortJsonWriter() {
        return new ShortJsonWriter();
    }

    @DefaultComponent
    default JsonReader<Short> shortJsonReader() {
        return new ShortJsonReader();
    }

    @DefaultComponent
    default JsonWriter<Integer> integerJsonWriter() {
        return new IntegerJsonWriter();
    }

    @DefaultComponent
    default JsonReader<Integer> integerJsonReader() {
        return new IntegerJsonReader();
    }

    @DefaultComponent
    default JsonWriter<Long> longJsonWriter() {
        return new LongJsonWriter();
    }

    @DefaultComponent
    default JsonReader<Long> longJsonReader() {
        return new LongJsonReader();
    }

    @DefaultComponent
    default JsonWriter<Double> doubleJsonWriter() {
        return new DoubleJsonWriter();
    }

    @DefaultComponent
    default JsonReader<Double> doubleJsonReader() {
        return new DoubleJsonReader();
    }

    @DefaultComponent
    default JsonWriter<Float> floatJsonWriter() {
        return new FloatJsonWriter();
    }

    @DefaultComponent
    default JsonReader<Float> floatJsonReader() {
        return new FloatJsonReader();
    }

    @DefaultComponent
    default JsonWriter<String> stringJsonWriter() {
        return new StringJsonWriter();
    }

    @DefaultComponent
    default JsonReader<String> stringJsonReader() {
        return new StringJsonReader();
    }

    @DefaultComponent
    default JsonWriter<Boolean> booleanJsonWriter() {
        return new BooleanJsonWriter();
    }

    @DefaultComponent
    default JsonReader<Boolean> booleanJsonReader() {
        return new BooleanJsonReader();
    }

    @DefaultComponent
    default JsonWriter<BigDecimal> bigDecimalJsonWriter() {
        return new BigDecimalJsonWriter();
    }

    @DefaultComponent
    default JsonReader<BigDecimal> bigDecimalJsonReader() {
        return new BigDecimalJsonReader();
    }

    @DefaultComponent
    default JsonWriter<BigInteger> bigIntegerJsonWriter() {
        return new BigIntegerJsonWriter();
    }

    @DefaultComponent
    default JsonReader<BigInteger> bigIntegerJsonReader() {
        return new BigIntegerJsonReader();
    }

    @DefaultComponent
    default JsonWriter<byte[]> byteArrayJsonWriter() {
        return new ByteArrayJsonWriter();
    }

    @DefaultComponent
    default JsonReader<byte[]> byteArrayJsonReader() {
        return new ByteArrayJsonReader();
    }

    @DefaultComponent
    default JsonWriter<RawJson> rawJsonWriter() {
        return new RawJsonWriter();
    }

    @DefaultComponent
    default JsonReader<UUID> uuidJsonReader() {
        return new UuidJsonCodec();
    }

    @DefaultComponent
    default JsonWriter<UUID> uuidJsonWriter() {
        return new UuidJsonCodec();
    }

    @DefaultComponent
    default JsonWriter<LocalDate> localDateJsonWriter() {
        return new LocalDateJsonWriter();
    }

    @DefaultComponent
    default JsonReader<LocalDate> localDateJsonReader() {
        return new LocalDateJsonReader();
    }

    @DefaultComponent
    default JsonWriter<LocalTime> localTimeJsonWriter() {
        return new LocalTimeJsonWriter();
    }

    @DefaultComponent
    default JsonReader<LocalTime> localTimeJsonReader() {
        return new LocalTimeJsonReader();
    }

    @DefaultComponent
    default JsonWriter<LocalDateTime> localDateTimeJsonWriter() {
        return new LocalDateTimeJsonWriter();
    }

    @DefaultComponent
    default JsonReader<LocalDateTime> localDateTimeJsonReader() {
        return new LocalDateTimeJsonReader();
    }

    @DefaultComponent
    default JsonWriter<OffsetTime> offsetTimeJsonWriter() {
        return new OffsetTimeJsonWriter();
    }

    @DefaultComponent
    default JsonReader<OffsetTime> offsetTimeJsonReader() {
        return new OffsetTimeJsonReader();
    }

    @DefaultComponent
    default JsonWriter<OffsetDateTime> offsetDateTimeJsonWriter() {
        return new OffsetDateTimeJsonWriter();
    }

    @DefaultComponent
    default JsonReader<OffsetDateTime> offsetDateTimeJsonReader() {
        return new OffsetDateTimeJsonReader();
    }

    @DefaultComponent
    default JsonWriter<ZonedDateTime> zonedDateTimeJsonWriter() {
        return new ZonedDateTimeJsonWriter();
    }

    @DefaultComponent
    default JsonReader<ZonedDateTime> zonedDateTimeJsonReader() {
        return new ZonedDateTimeJsonReader();
    }

    @DefaultComponent
    default JsonWriter<Instant> instantJsonWriter() {
        return new InstantJsonWriter();
    }

    @DefaultComponent
    default JsonReader<Instant> instantJsonReader() {
        return new InstantJsonReader();
    }

    @DefaultComponent
    default JsonWriter<Year> yearJsonWriter() {
        return new YearJsonWriter();
    }

    @DefaultComponent
    default JsonReader<Year> yearJsonReader() {
        return new YearJsonReader();
    }

    @DefaultComponent
    default JsonWriter<YearMonth> yearMonthJsonWriter() {
        return new YearMonthJsonWriter();
    }

    @DefaultComponent
    default JsonReader<YearMonth> yearMonthJsonReader() {
        return new YearMonthJsonReader();
    }

    @DefaultComponent
    default JsonWriter<MonthDay> monthDayJsonWriter() {
        return new MonthDayJsonWriter();
    }

    @DefaultComponent
    default JsonReader<MonthDay> monthDayJsonReader() {
        return new MonthDayJsonReader();
    }

    @DefaultComponent
    default JsonWriter<Month> monthJsonWriter() {
        return new MonthJsonWriter();
    }

    @DefaultComponent
    default JsonReader<Month> monthJsonReader() {
        return new MonthJsonReader();
    }

    @DefaultComponent
    default JsonWriter<DayOfWeek> dayOfWeekJsonWriter() {
        return new DayOfWeekJsonWriter();
    }

    @DefaultComponent
    default JsonReader<DayOfWeek> dayOfWeekJsonReader() {
        return new DayOfWeekJsonReader();
    }

    @DefaultComponent
    default JsonWriter<ZoneId> zoneIdJsonWriter() {
        return new ZoneIdJsonWriter();
    }

    @DefaultComponent
    default JsonReader<ZoneId> zoneIdJsonReader() {
        return new ZoneIdJsonReader();
    }

    @DefaultComponent
    default JsonWriter<Duration> durationJsonWriter() {
        return new DurationJsonWriter();
    }

    @DefaultComponent
    default JsonReader<Duration> durationJsonReader() {
        return new DurationJsonReader();
    }
}

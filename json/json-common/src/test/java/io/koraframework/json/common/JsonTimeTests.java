package io.koraframework.json.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;

class JsonTimeTests extends Assertions implements JsonModule {

    @Test
    void localDateDeserializedAndSerialized() throws IOException {
        // given
        var writer = localDateJsonWriter();
        var reader = localDateJsonReader();
        var value = LocalDate.now();

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value, valueRestored);
    }

    @Test
    void localTimeDeserializedAndSerialized() throws IOException {
        // given
        var writer = localTimeJsonWriter();
        var reader = localTimeJsonReader();
        var value = LocalTime.now();

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value.truncatedTo(ChronoUnit.MILLIS), valueRestored);
    }

    @Test
    void localDateTimeDeserializedAndSerialized() throws IOException {
        // given
        var writer = localDateTimeJsonWriter();
        var reader = localDateTimeJsonReader();
        var value = LocalDateTime.now();

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value.truncatedTo(ChronoUnit.MILLIS), valueRestored);
    }

    @Test
    void offsetTimeDeserializedAndSerialized() throws IOException {
        // given
        var writer = offsetTimeJsonWriter();
        var reader = offsetTimeJsonReader();
        var value = OffsetTime.now();

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value.truncatedTo(ChronoUnit.MILLIS), valueRestored);
    }

    @Test
    void offsetDateTimeDeserializedAndSerialized() throws IOException {
        // given
        var writer = offsetDateTimeJsonWriter();
        var reader = offsetDateTimeJsonReader();
        var value = OffsetDateTime.now();

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value.truncatedTo(ChronoUnit.MILLIS), valueRestored);
    }

    @Test
    void zonedDateTimeDeserializedAndSerialized() throws IOException {
        // given
        var writer = zonedDateTimeJsonWriter();
        var reader = zonedDateTimeJsonReader();
        var value = ZonedDateTime.now();

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value.truncatedTo(ChronoUnit.MILLIS), valueRestored);
    }

    @Test
    void instantDeserializedAndSerialized() throws IOException {
        // given
        var writer = instantJsonWriter();
        var reader = instantJsonReader();
        var value = Instant.now();

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value, valueRestored);
    }

    @Test
    void yearDeserializedAndSerialized() throws IOException {
        // given
        var writer = yearJsonWriter();
        var reader = yearJsonReader();
        var value = Year.now();

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value, valueRestored);
    }

    @Test
    void yearMonthDeserializedAndSerialized() throws IOException {
        // given
        var writer = yearMonthJsonWriter();
        var reader = yearMonthJsonReader();
        var value = YearMonth.now();

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value, valueRestored);
    }

    @Test
    void monthDeserializedAndSerialized() throws IOException {
        // given
        var writer = monthJsonWriter();
        var reader = monthJsonReader();
        var value = Month.AUGUST;

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value, valueRestored);
    }

    @Test
    void monthDayDeserializedAndSerialized() throws IOException {
        // given
        var writer = monthDayJsonWriter();
        var reader = monthDayJsonReader();
        var value = MonthDay.now();

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value, valueRestored);
    }

    @Test
    void dayOfWeekDeserializedAndSerialized() throws IOException {
        // given
        var writer = dayOfWeekJsonWriter();
        var reader = dayOfWeekJsonReader();
        var value = DayOfWeek.SUNDAY;

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value, valueRestored);
    }

    @Test
    void zoneIdDeserializedAndSerialized() throws IOException {
        // given
        var writer = zoneIdJsonWriter();
        var reader = zoneIdJsonReader();
        var value = ZoneId.systemDefault();

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value, valueRestored);
    }

    @Test
    void durationDeserializedAndSerialized() throws IOException {
        // given
        var writer = durationJsonWriter();
        var reader = durationJsonReader();
        var value = Duration.ofSeconds(15);

        // when
        final byte[] valueAsBytes = writer.toByteArray(value);
        assertNotEquals(0, valueAsBytes.length);

        // then
        var valueRestored = reader.read(valueAsBytes);
        assertEquals(value, valueRestored);
    }

    @Test
    void yearsOutsideFourDigitsRoundTrip() {
        assertRoundTrip(yearJsonWriter(), yearJsonReader(), Year.of(10000), "\"+10000\"");
        assertRoundTrip(yearJsonWriter(), yearJsonReader(), Year.of(-44), "\"-0044\"");
        assertRoundTrip(yearMonthJsonWriter(), yearMonthJsonReader(), YearMonth.of(10000, 1), "\"+10000-01\"");
        assertRoundTrip(localDateTimeJsonWriter(), localDateTimeJsonReader(), LocalDateTime.of(10000, 1, 1, 0, 0), "\"+10000-01-01T00:00:00.000\"");
        assertRoundTrip(localDateTimeJsonWriter(), localDateTimeJsonReader(), LocalDateTime.MAX.truncatedTo(ChronoUnit.MILLIS), "\"+999999999-12-31T23:59:59.999\"");
        assertRoundTrip(offsetDateTimeJsonWriter(), offsetDateTimeJsonReader(), OffsetDateTime.MAX.truncatedTo(ChronoUnit.MILLIS), "\"+999999999-12-31T23:59:59.999-18:00\"");
        assertRoundTrip(offsetDateTimeJsonWriter(), offsetDateTimeJsonReader(), OffsetDateTime.MIN, "\"-999999999-01-01T00:00:00.000+18:00\"");
    }

    private static <T> void assertRoundTrip(JsonWriter<T> writer, JsonReader<T> reader, T value, String expectedJson) {
        var bytes = writer.toByteArray(value);
        assertEquals(expectedJson, new String(bytes, StandardCharsets.UTF_8));
        assertEquals(value, reader.read(bytes));
    }
}

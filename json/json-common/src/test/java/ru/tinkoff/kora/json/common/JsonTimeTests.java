package ru.tinkoff.kora.json.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalAmount;
import java.util.Date;

class JsonTimeTests extends Assertions implements JsonCommonModule {

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
}

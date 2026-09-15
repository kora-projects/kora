package io.koraframework.scheduling.jdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CronExpressionTest {

    @ParameterizedTest
    @MethodSource("nextFireTime")
    void shouldEvaluateNextFireTime(String expression, String after, String expected) {
        var cron = CronExpression.parse(expression);

        var next = cron.next(LocalDateTime.parse(after));

        assertThat(next).isEqualTo(LocalDateTime.parse(expected));
    }

    @ParameterizedTest
    @MethodSource("invalidExpressions")
    void shouldRejectInvalidExpressions(String expression, String message) {
        assertThatThrownBy(() -> CronExpression.parse(expression))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(message);
    }

    @Test
    void shouldEvaluateZonedDateTimeInOriginalZone() {
        var zone = ZoneId.of("Europe/Moscow");
        var after = ZonedDateTime.of(2026, 6, 14, 9, 59, 59, 0, zone);

        var next = CronExpression.parse("0 0 10 * * ?").next(after);

        assertThat(next).isEqualTo(ZonedDateTime.of(2026, 6, 14, 10, 0, 0, 0, zone));
    }

    @Test
    void shouldReturnNullWhenExpressionHasNoNextFireTime() {
        var next = CronExpression.parse("0 0 0 1 1 ? 2099")
            .next(LocalDateTime.parse("2099-01-01T00:00:00"));

        assertThat(next).isNull();
    }

    @ParameterizedTest
    @MethodSource("zonedFireTimes")
    void shouldHandleZoneTransitionsInInstantOrder(String expression, String after, String expected) {
        var lowerBound = ZonedDateTime.parse(after);
        var next = CronExpression.parse(expression).next(lowerBound);
        assertThat(next).isEqualTo(ZonedDateTime.parse(expected));
        assertThat(next.toInstant()).isAfter(lowerBound.toInstant());
    }

    static Stream<Arguments> zonedFireTimes() {
        return Stream.of(
            // First and second occurrences of the repeated hour.
            Arguments.of("0 30 2 * * ?", "2026-10-25T02:15+02:00[Europe/Berlin]", "2026-10-25T02:30+02:00[Europe/Berlin]"),
            Arguments.of("0 30 2 * * ?", "2026-10-25T02:15+01:00[Europe/Berlin]", "2026-10-25T02:30+01:00[Europe/Berlin]"),
            Arguments.of("0 30 2 * * ?", "2026-10-25T02:30+02:00[Europe/Berlin]", "2026-10-25T02:30+01:00[Europe/Berlin]"),
            Arguments.of("0 30 2 * * ?", "2026-10-25T02:45+02:00[Europe/Berlin]", "2026-10-25T02:30+01:00[Europe/Berlin]"),
            Arguments.of("0 30 2 * * ?", "2026-10-25T02:30+01:00[Europe/Berlin]", "2026-10-26T02:30+01:00[Europe/Berlin]"),
            // A trigger exactly at the transition must not be skipped.
            Arguments.of("* * * * * *", "2026-10-25T02:59:59+02:00[Europe/Berlin]", "2026-10-25T02:00+01:00[Europe/Berlin]"),
            Arguments.of("* * * * * *", "2026-03-29T01:59:59+01:00[Europe/Berlin]", "2026-03-29T03:00+02:00[Europe/Berlin]"),
            // Nonexistent local times are skipped rather than shifted.
            Arguments.of("0 30 2 * * ?", "2026-03-29T01:45+01:00[Europe/Berlin]", "2026-03-30T02:30+02:00[Europe/Berlin]"),
            // Not every offset transition is one hour long.
            Arguments.of("0 45 1 * * ?", "2026-04-05T01:50+11:00[Australia/Lord_Howe]", "2026-04-05T01:45+10:30[Australia/Lord_Howe]")
        );
    }

    static Stream<Arguments> nextFireTime() {
        return Stream.of(
            Arguments.of("0 0 0 1 JUL ?", "2026-06-14T00:00:00", "2026-07-01T00:00:00"),
            Arguments.of("0 0 0 1 OCT ?", "2026-06-14T00:00:00", "2026-10-01T00:00:00"),
            Arguments.of("0 0 0 25 DEC ?", "2026-06-14T00:00:00", "2026-12-25T00:00:00"),
            Arguments.of("0 0 12 ? * WED", "2026-06-14T00:00:00", "2026-06-17T12:00:00"),
            Arguments.of("0 0 0 1 JUL,DEC ?", "2026-07-01T00:00:00", "2026-12-01T00:00:00"),
            Arguments.of("0 0 0 1 JUL-OCT ?", "2026-08-02T00:00:00", "2026-09-01T00:00:00"),
            Arguments.of("0 0 0 1 JUL/2 ?", "2026-07-01T00:00:00", "2026-09-01T00:00:00"),
            Arguments.of("0 0 12 ? * WED-FRI", "2026-06-14T00:00:00", "2026-06-17T12:00:00"),
            Arguments.of("*/10 * * * * *", "2026-06-14T12:00:00", "2026-06-14T12:00:10"),
            Arguments.of("*/10 * * * * *", "2026-06-14T12:00:09", "2026-06-14T12:00:10"),
            Arguments.of("0 */5 * * * *", "2026-06-14T12:03:59", "2026-06-14T12:05:00"),
            Arguments.of("0 0 0 1 * ?", "2026-01-01T00:00:00", "2026-02-01T00:00:00"),
            Arguments.of("0 0 0 29 FEB ?", "2023-03-01T00:00:00", "2024-02-29T00:00:00"),
            Arguments.of("0 0 12 ? * MON", "2026-06-14T12:00:00", "2026-06-15T12:00:00"),
            Arguments.of("0 0 0 ? * 0", "2026-06-13T23:59:59", "2026-06-14T00:00:00"),
            Arguments.of("0 0 9-17 * * MON-FRI", "2026-06-15T16:59:59", "2026-06-15T17:00:00"),
            Arguments.of("0 0 9-17 * * MON-FRI", "2026-06-15T17:00:00", "2026-06-16T09:00:00"),
            Arguments.of("5/10 0,30 8-10 * JAN,MAR MON-FRI", "2024-01-01T07:59:59", "2024-01-01T08:00:05"),
            Arguments.of("0 0 0 1 JAN ?", "2026-06-14T00:00:00", "2027-01-01T00:00:00"),
            Arguments.of("0 0 0 * * ? 2027", "2026-12-31T23:59:59", "2027-01-01T00:00:00"),
            Arguments.of("*/15 9-17 * * MON-FRI", "2026-06-15T08:59:59", "2026-06-15T09:00:00"),
            Arguments.of("*/15 9-17 * * MON-FRI", "2026-06-15T09:00:00", "2026-06-15T09:15:00")
        );
    }

    static Stream<Arguments> invalidExpressions() {
        return Stream.of(
            Arguments.of("", "5, 6 or 7 fields"),
            Arguments.of("* * * *", "5, 6 or 7 fields"),
            Arguments.of("* * * * * * * *", "5, 6 or 7 fields"),
            Arguments.of("60 * * * * *", "out of range"),
            Arguments.of("* 60 * * * *", "out of range"),
            Arguments.of("* * 24 * * *", "out of range"),
            Arguments.of("* * * 32 * *", "out of range"),
            Arguments.of("* * * * 13 *", "out of range"),
            Arguments.of("* * * * * 8", "out of range"),
            Arguments.of("*/0 * * * * *", "step must be positive"),
            Arguments.of("10-5 * * * * *", "range start is greater"),
            Arguments.of("* * * L * ?", "doesn't support"),
            Arguments.of("* * * 1W * ?", "doesn't support"),
            Arguments.of("* * * ? * MON#1", "doesn't support"),
            Arguments.of("? * * * * *", "'?' is not supported")
        );
    }
}

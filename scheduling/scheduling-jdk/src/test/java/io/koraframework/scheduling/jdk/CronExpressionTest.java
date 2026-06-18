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

    static Stream<Arguments> nextFireTime() {
        return Stream.of(
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

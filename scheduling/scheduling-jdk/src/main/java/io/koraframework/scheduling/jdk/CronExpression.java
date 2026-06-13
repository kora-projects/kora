package io.koraframework.scheduling.jdk;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.BitSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Parser and evaluator for cron expressions used by the JDK scheduler.
 * <p>
 * The expression may contain five, six or seven single space-separated fields:
 * <pre>
 * {@code
 * 5 fields: minute hour day-of-month month day-of-week
 * 6 fields: second minute hour day-of-month month day-of-week
 * 7 fields: second minute hour day-of-month month day-of-week year
 *
 * ┌───────────── second (0-59)
 * │ ┌───────────── minute (0-59)
 * │ │ ┌───────────── hour (0-23)
 * │ │ │ ┌───────────── day of the month (1-31)
 * │ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
 * │ │ │ │ │ ┌───────────── day of the week (1-7 or SUN-SAT)
 * │ │ │ │ │ │ ┌───────────── year (empty, 1970-2099, ?)
 * │ │ │ │ │ │ │
 * │ │ │ │ │ │ │
 * * * * * * * *
 * }
 * </pre>
 * <p>
 * Five-field expressions omit the seconds field and are evaluated with {@code 0} seconds.
 * The year field is optional; when it is absent, all years from 1970 to 2099 are allowed.
 * Day-of-week also accepts {@code 0} as Sunday.
 * <p>
 * Supported special characters:
 * <ul>
 * <li>{@code *} selects all values in the field, for example {@code *} in the minute field means every minute.</li>
 * <li>{@code ?} means no specific value and is supported only in day-of-month, day-of-week and year fields.</li>
 * <li>{@code ,} separates explicit values, for example {@code 6,18} in the hour field.</li>
 * <li>{@code -} selects an inclusive range, for example {@code MON-FRI} or {@code 9-17}.</li>
 * <li>{@code /} selects values by step, for example {@code *&#47;10} in the seconds field or {@code 5/10}.</li>
 * </ul>
 * <p>
 * Quartz-specific modifiers {@code L}, {@code W}, {@code #} and {@code C} are not supported by this evaluator.
 * <p>
 * Example expressions:
 * <ul>
 * <li>{@code "0 * * * * *"} = the top of every minute.</li>
 * <li>{@code "*&#47;10 * * * * *"} = every ten seconds.</li>
 * <li>{@code "0 0 * * * ?"} = the top of every hour.</li>
 * <li>{@code "0 0 6,19 * * ?"} = 6:00 and 19:00 every day.</li>
 * <li>{@code "0 0/30 8-10 * * ?"} = every 30 minutes from 8:00 through 10:30 every day.</li>
 * <li>{@code "0 0 9-17 ? * MON-FRI"} = every hour from 9:00 through 17:00 on weekdays.</li>
 * <li>{@code "0 0 0 25 DEC ?"} = every Christmas Day at midnight.</li>
 * <li>{@code "0 0 0 29 FEB ?"} = every leap day at midnight.</li>
 * <li>{@code "0 0 0 1 JAN ? 2027"} = January 1, 2027 at midnight.</li>
 * </ul>
 */
public final class CronExpression {

    private static final int MAX_YEAR = 2099;
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
        Map.entry("JAN", 1),
        Map.entry("FEB", 2),
        Map.entry("MAR", 3),
        Map.entry("APR", 4),
        Map.entry("MAY", 5),
        Map.entry("JUN", 6),
        Map.entry("JUL", 7),
        Map.entry("AUG", 8),
        Map.entry("SEP", 9),
        Map.entry("OCT", 10),
        Map.entry("NOV", 11),
        Map.entry("DEC", 12)
    );
    private static final Map<String, Integer> DAYS = Map.of(
        "SUN", 1,
        "MON", 2,
        "TUE", 3,
        "WED", 4,
        "THU", 5,
        "FRI", 6,
        "SAT", 7
    );

    private final String source;
    private final CronField seconds;
    private final CronField minutes;
    private final CronField hours;
    private final CronField daysOfMonth;
    private final CronField months;
    private final CronField daysOfWeek;
    private final CronField years;

    private CronExpression(String source,
                           CronField seconds,
                           CronField minutes,
                           CronField hours,
                           CronField daysOfMonth,
                           CronField months,
                           CronField daysOfWeek,
                           CronField years) {
        this.source = source;
        this.seconds = seconds;
        this.minutes = minutes;
        this.hours = hours;
        this.daysOfMonth = daysOfMonth;
        this.months = months;
        this.daysOfWeek = daysOfWeek;
        this.years = years;
    }

    /**
     * Parses the supplied cron expression.
     *
     * @param expression cron expression with five, six or seven fields
     * @return parsed expression
     * @throws IllegalArgumentException when the expression has invalid syntax or unsupported fields
     */
    public static CronExpression parse(String expression) {
        Objects.requireNonNull(expression, "expression");
        var fields = expression.trim().split("\\s+");
        if (fields.length == 5) {
            fields = new String[]{"0", fields[0], fields[1], fields[2], fields[3], fields[4]};
        }
        if (fields.length != 6 && fields.length != 7) {
            throw new IllegalArgumentException("Cron expression must consist of 5, 6 or 7 fields: " + expression);
        }

        var seconds = CronField.parse(fields[0], 0, 59, null, false);
        var minutes = CronField.parse(fields[1], 0, 59, null, false);
        var hours = CronField.parse(fields[2], 0, 23, null, false);
        var daysOfMonth = CronField.parse(fields[3], 1, 31, null, true);
        var months = CronField.parse(fields[4], 1, 12, MONTHS, false);
        var daysOfWeek = CronField.parse(fields[5], 1, 7, DAYS, true);
        var years = fields.length == 7
            ? CronField.parse(fields[6], 1970, MAX_YEAR, null, true)
            : CronField.all(1970, MAX_YEAR, false);
        return new CronExpression(expression, seconds, minutes, hours, daysOfMonth, months, daysOfWeek, years);
    }

    /**
     * Evaluates the next fire time strictly after the supplied date-time and preserves its zone.
     *
     * @param after lower bound, exclusive
     * @return next fire time or {@code null} when the expression cannot fire before year 2100
     */
    public ZonedDateTime next(ZonedDateTime after) {
        var next = this.next(after.toLocalDateTime());
        return next == null ? null : next.atZone(after.getZone());
    }

    /**
     * Evaluates the next fire time strictly after the supplied date-time.
     *
     * @param after lower bound, exclusive
     * @return next fire time or {@code null} when the expression cannot fire before year 2100
     */
    public LocalDateTime next(LocalDateTime after) {
        var next = after.plusSeconds(1).truncatedTo(ChronoUnit.SECONDS);
        while (next.getYear() <= MAX_YEAR) {
            var year = this.nextValue(this.years, next.getYear());
            if (year < 0) {
                return null;
            }
            if (year != next.getYear()) {
                next = LocalDateTime.of(year, 1, 1, 0, 0, 0);
            }

            var month = this.nextValue(this.months, next.getMonthValue());
            if (month < 0) {
                next = LocalDateTime.of(next.getYear() + 1, 1, 1, 0, 0, 0);
                continue;
            }
            if (month != next.getMonthValue()) {
                next = LocalDateTime.of(next.getYear(), month, 1, 0, 0, 0);
            }

            if (!this.dayMatches(next.toLocalDate())) {
                next = next.plusDays(1).truncatedTo(ChronoUnit.DAYS);
                continue;
            }

            var hour = this.nextValue(this.hours, next.getHour());
            if (hour < 0) {
                next = next.plusDays(1).truncatedTo(ChronoUnit.DAYS);
                continue;
            }
            if (hour != next.getHour()) {
                next = next.withHour(hour).withMinute(0).withSecond(0);
            }

            var minute = this.nextValue(this.minutes, next.getMinute());
            if (minute < 0) {
                next = next.plusHours(1).truncatedTo(ChronoUnit.HOURS);
                continue;
            }
            if (minute != next.getMinute()) {
                next = next.withMinute(minute).withSecond(0);
            }

            var second = this.nextValue(this.seconds, next.getSecond());
            if (second < 0) {
                next = next.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
                continue;
            }
            if (second != next.getSecond()) {
                next = next.withSecond(second);
            }

            if (this.matches(next)) {
                return next;
            }
            next = next.plusSeconds(1);
        }
        return null;
    }

    private boolean matches(LocalDateTime value) {
        return this.years.contains(value.getYear())
            && this.months.contains(value.getMonthValue())
            && this.dayMatches(value.toLocalDate())
            && this.hours.contains(value.getHour())
            && this.minutes.contains(value.getMinute())
            && this.seconds.contains(value.getSecond());
    }

    private boolean dayMatches(LocalDate date) {
        if (!this.daysOfMonth.contains(date.getDayOfMonth())) {
            return false;
        }
        if (!this.daysOfWeek.contains(dayOfWeek(date.getDayOfWeek()))) {
            return false;
        }
        return date.getDayOfMonth() <= Month.of(date.getMonthValue()).length(date.isLeapYear());
    }

    private int nextValue(CronField field, int from) {
        return field.nextOrSame(from);
    }

    private static int dayOfWeek(DayOfWeek dayOfWeek) {
        return dayOfWeek == DayOfWeek.SUNDAY ? 1 : dayOfWeek.getValue() + 1;
    }

    @Override
    public String toString() {
        return this.source;
    }

    private record CronField(BitSet values, int min, int max, boolean noSpecific) {

        static CronField all(int min, int max, boolean noSpecific) {
            var values = new BitSet(max + 1);
            values.set(min, max + 1);
            return new CronField(values, min, max, noSpecific);
        }

        static CronField parse(String expression, int min, int max, Map<String, Integer> aliases, boolean supportsNoSpecific) {
            var normalized = expression.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Cron field is empty");
            }
            if (normalized.indexOf('L') >= 0 || normalized.indexOf('W') >= 0 || normalized.indexOf('#') >= 0 || normalized.indexOf('C') >= 0) {
                throw new IllegalArgumentException("Cron field doesn't support L, W, # or C modifiers: " + expression);
            }
            if ("?".equals(normalized)) {
                if (!supportsNoSpecific) {
                    throw new IllegalArgumentException("'?' is not supported for this cron field: " + expression);
                }
                return all(min, max, true);
            }

            var values = new BitSet(max + 1);
            for (var token : normalized.split(",")) {
                parsePart(token, values, min, max, aliases);
            }
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Cron field has no values: " + expression);
            }
            return new CronField(values, min, max, false);
        }

        boolean contains(int value) {
            return value >= this.min && value <= this.max && this.values.get(value);
        }

        int nextOrSame(int value) {
            var next = this.values.nextSetBit(Math.max(value, this.min));
            return next <= this.max ? next : -1;
        }

        private static void parsePart(String part, BitSet values, int min, int max, Map<String, Integer> aliases) {
            if (part.isBlank()) {
                throw new IllegalArgumentException("Cron field contains empty part");
            }
            var step = 1;
            var range = part;
            var slash = part.indexOf('/');
            if (slash >= 0) {
                range = part.substring(0, slash);
                step = Integer.parseInt(part.substring(slash + 1));
                if (step <= 0) {
                    throw new IllegalArgumentException("Cron field step must be positive: " + part);
                }
            }

            int from;
            int to;
            if ("*".equals(range) || "?".equals(range)) {
                from = min;
                to = max;
            } else {
                var dash = range.indexOf('-');
                if (dash >= 0) {
                    from = parseValue(range.substring(0, dash), min, max, aliases);
                    to = parseValue(range.substring(dash + 1), min, max, aliases);
                } else if (slash >= 0) {
                    from = parseValue(range, min, max, aliases);
                    to = max;
                } else {
                    from = parseValue(range, min, max, aliases);
                    to = from;
                }
            }

            if (from > to) {
                throw new IllegalArgumentException("Cron field range start is greater than end: " + part);
            }
            for (var value = from; value <= to; value += step) {
                values.set(value);
            }
        }

        private static int parseValue(String value, int min, int max, Map<String, Integer> aliases) {
            var alias = aliases == null ? null : aliases.get(value);
            var parsed = alias == null ? Integer.parseInt(value) : alias;
            if (max == 7 && parsed == 0) {
                parsed = 1;
            }
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException("Cron field value is out of range [" + min + ", " + max + "]: " + value);
            }
            return parsed;
        }
    }
}

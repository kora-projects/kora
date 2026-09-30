package io.koraframework.scheduling.annotation.processor;

import io.koraframework.annotation.processor.common.ProcessingErrorException;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Compile-time check of CRON expressions declared in scheduling annotations.
 * <p>
 * The check is intentionally incomplete: it reports only errors that the runtime parser of the target scheduler
 * rejects as well, and accepts every construct it does not model. Such expressions are still validated by the
 * scheduler on application start. {@code CronValidatorTest} compares the check with the real parsers.
 */
final class CronValidator {

    enum Dialect {
        JDK("@ScheduleJdkWithCron", """
            The JDK scheduler expects 5, 6 or 7 fields, the second and year fields are optional:
            ┌───────────── second (0-59, optional)
            │ ┌───────────── minute (0-59)
            │ │ ┌───────────── hour (0-23)
            │ │ │ ┌───────────── day of the month (1-31 or ?)
            │ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
            │ │ │ │ │ ┌───────────── day of the week (1-7 or SUN-SAT, 0 and 1 are Sunday, or ?)
            │ │ │ │ │ │ ┌───────────── year (1970-2099 or ?, optional)
            │ │ │ │ │ │ │
            * * * * * * *
            Modifiers L, W, # and C are not supported.""", "0 0 15 * * ?", "0 0 9-17 * * MON-FRI"),
        QUARTZ("@ScheduleQuartzWithCron", """
            Quartz expects 6 or 7 fields, the year field is optional, and exactly one of day-of-month and day-of-week must be '?':
            ┌───────────── second (0-59)
            │ ┌───────────── minute (0-59)
            │ │ ┌───────────── hour (0-23)
            │ │ │ ┌───────────── day of the month (1-31, L, W or ?)
            │ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
            │ │ │ │ │ ┌───────────── day of the week (1-7 or SUN-SAT, 1 is Sunday, L, # or ?)
            │ │ │ │ │ │ ┌───────────── year (optional)
            │ │ │ │ │ │ │
            * * * ? * * *""", "0 0 15 * * ?", "0 0 9-17 ? * MON-FRI"),
        DB("@ScheduleDbWithCron", """
            The database scheduler expects exactly 6 fields, a macro such as @daily, or '-' to disable the job:
            ┌───────────── second (0-59)
            │ ┌───────────── minute (0-59)
            │ │ ┌───────────── hour (0-23)
            │ │ │ ┌───────────── day of the month (1-31, L, W or ?)
            │ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
            │ │ │ │ │ ┌───────────── day of the week (0-7 or MON-SUN, 0 and 7 are Sunday, L, # or ?)
            │ │ │ │ │ │
            * * * * * *""", "0 0 15 * * *", "0 0 9-17 * * MON-FRI");

        private final String annotation;
        private final String format;
        private final String dailyExample;
        private final String weekdaysExample;

        Dialect(String annotation, String format, String dailyExample, String weekdaysExample) {
            this.annotation = annotation;
            this.format = format;
            this.dailyExample = dailyExample;
            this.weekdaysExample = weekdaysExample;
        }

        String annotation() {
            return this.annotation;
        }

        String format() {
            return this.format;
        }

        /**
         * @return expressions accepted by the scheduler, described in {@link #examplesDescription()}
         */
        List<String> examples() {
            return List.of(this.dailyExample, this.weekdaysExample);
        }

        String examplesDescription() {
            return """
                Examples:
                  '%s' runs every day at 15:00
                  '%s' runs every hour from 9:00 through 17:00 on weekdays""".formatted(this.dailyExample, this.weekdaysExample);
        }
    }

    private enum Kind {SECOND, MINUTE, HOUR, DAY_OF_MONTH, MONTH, DAY_OF_WEEK, YEAR}

    private record Field(Kind kind, String name, int min, int max, Map<String, Integer> names, boolean noSpecific) {}

    private static final Pattern SIMPLE_ELEMENT = Pattern.compile("^(\\*|[0-9]+|[A-Z]+)(?:-([0-9]+|[A-Z]+))?(?:/([0-9]+))?$");

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
        Map.entry("JAN", 1), Map.entry("FEB", 2), Map.entry("MAR", 3), Map.entry("APR", 4),
        Map.entry("MAY", 5), Map.entry("JUN", 6), Map.entry("JUL", 7), Map.entry("AUG", 8),
        Map.entry("SEP", 9), Map.entry("OCT", 10), Map.entry("NOV", 11), Map.entry("DEC", 12)
    );
    private static final Map<String, Integer> DAYS_FROM_SUNDAY = Map.of(
        "SUN", 1, "MON", 2, "TUE", 3, "WED", 4, "THU", 5, "FRI", 6, "SAT", 7
    );
    private static final Map<String, Integer> DAYS_FROM_MONDAY = Map.of(
        "MON", 1, "TUE", 2, "WED", 3, "THU", 4, "FRI", 5, "SAT", 6, "SUN", 7
    );

    private static final Field SECOND = new Field(Kind.SECOND, "second", 0, 59, Map.of(), false);
    private static final Field MINUTE = new Field(Kind.MINUTE, "minute", 0, 59, Map.of(), false);
    private static final Field HOUR = new Field(Kind.HOUR, "hour", 0, 23, Map.of(), false);
    private static final Field DAY_OF_MONTH = new Field(Kind.DAY_OF_MONTH, "day-of-month", 1, 31, Map.of(), true);
    private static final Field MONTH = new Field(Kind.MONTH, "month", 1, 12, MONTHS, false);

    private static final Field[] JDK_FIELDS = {
        SECOND, MINUTE, HOUR, DAY_OF_MONTH, MONTH,
        new Field(Kind.DAY_OF_WEEK, "day-of-week", 0, 7, DAYS_FROM_SUNDAY, true),
        new Field(Kind.YEAR, "year", 1970, 2099, Map.of(), true)
    };
    // Quartz accepts any year value, so the year field is not checked
    private static final Field[] QUARTZ_FIELDS = {
        SECOND, MINUTE, HOUR, DAY_OF_MONTH, MONTH,
        new Field(Kind.DAY_OF_WEEK, "day-of-week", 1, 7, DAYS_FROM_SUNDAY, true),
        null
    };
    private static final Field[] DB_FIELDS = {
        SECOND, MINUTE, HOUR, DAY_OF_MONTH, MONTH,
        new Field(Kind.DAY_OF_WEEK, "day-of-week", 0, 7, DAYS_FROM_MONDAY, true)
    };

    private CronValidator() {}

    /**
     * @throws ProcessingErrorException when the scheduler would reject the non-blank expression
     */
    static void check(Dialect dialect, String expression, TypeElement type, Element method, AnnotationMirror annotation) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        var error = validate(dialect, expression);
        if (error != null) {
            throw new ProcessingErrorException(
                "Invalid CRON expression '%s' in %s on '%s#%s()': %s.\n%s\n%s\nSee the Javadoc of %s for details."
                    .formatted(expression, dialect.annotation(), type.getQualifiedName(), method.getSimpleName(), error, dialect.format(), dialect.examplesDescription(), dialect.annotation()),
                method,
                annotation
            );
        }
    }

    /**
     * @return description of an error the scheduler would report for the expression, or {@code null} when no such error is found
     */
    static String validate(Dialect dialect, String expression) {
        var trimmed = expression.trim();
        if (dialect == Dialect.DB && (trimmed.equals("-") || trimmed.startsWith("@"))) {
            return null;
        }
        var values = trimmed.split("\\s+");
        var fields = switch (dialect) {
            case JDK -> {
                if (values.length < 5 || values.length > 7) {
                    yield null;
                }
                if (values.length == 5) {
                    var withSeconds = new String[6];
                    withSeconds[0] = "0";
                    System.arraycopy(values, 0, withSeconds, 1, 5);
                    values = withSeconds;
                }
                yield JDK_FIELDS;
            }
            case QUARTZ -> values.length < 6 || values.length > 7 ? null : QUARTZ_FIELDS;
            case DB -> values.length != 6 ? null : DB_FIELDS;
        };
        if (fields == null) {
            return "expression has %d fields".formatted(trimmed.isEmpty() ? 0 : values.length);
        }

        for (int i = 0; i < values.length; i++) {
            var field = fields[i];
            if (field == null) {
                continue;
            }
            var error = validateField(dialect, field, values[i].toUpperCase(Locale.ROOT));
            if (error != null) {
                return error;
            }
        }

        if (dialect == Dialect.QUARTZ) {
            var dayOfMonth = values[3];
            var dayOfWeek = values[5];
            var dayOfMonthUnspecified = dayOfMonth.equals("?");
            var dayOfWeekUnspecified = dayOfWeek.equals("?");
            var simple = (dayOfMonthUnspecified || !dayOfMonth.contains("?")) && (dayOfWeekUnspecified || !dayOfWeek.contains("?"))
                && !quartzSpecialValue(dayOfMonth) && !quartzSpecialValue(dayOfWeek);
            if (simple && dayOfMonthUnspecified == dayOfWeekUnspecified) {
                return "'?' must be used in exactly one of the day-of-month and day-of-week fields";
            }
        }
        return null;
    }

    private static String validateField(Dialect dialect, Field field, String value) {
        if (value.equals("?")) {
            return field.noSpecific() ? null : "'?' is not allowed in the %s field".formatted(field.name());
        }
        for (var element : value.split(",", -1)) {
            if (element.contains("?")) {
                // the JDK scheduler reads '?' inside a list as '*', Quartz accepts it after a value, for example '*-?'
                if (!field.noSpecific() && dialect != Dialect.JDK && element.startsWith("?")) {
                    return "'?' is not allowed in the %s field".formatted(field.name());
                }
                continue;
            }
            if (dialect == Dialect.JDK) {
                var error = jdkModifier(field, element);
                if (error != null) {
                    return error;
                }
            }
            var matcher = SIMPLE_ELEMENT.matcher(element);
            if (!matcher.matches()) {
                continue;
            }
            var base = matcher.group(1);
            var end = matcher.group(2);
            var step = matcher.group(3);
            if (base.equals("*") && end != null) {
                continue;
            }

            Integer from = null;
            if (!base.equals("*")) {
                var parsed = parseValue(dialect, field, base);
                if (parsed == null) {
                    continue;
                }
                if (parsed instanceof String error) {
                    return error;
                }
                from = (Integer) parsed;
            }
            Integer to = null;
            // Quartz does not check the end of a range
            if (end != null && dialect != Dialect.QUARTZ) {
                var parsed = parseValue(dialect, field, end);
                if (parsed == null) {
                    continue;
                }
                if (parsed instanceof String error) {
                    return error;
                }
                to = (Integer) parsed;
            }
            if (from != null && to != null && from > to && rejectsReversedRange(dialect, field)) {
                return "%s range %s is reversed".formatted(field.name(), element);
            }
            if (step != null) {
                var increment = step.length() > 9 ? Long.MAX_VALUE : Long.parseLong(step);
                if (increment == 0 && dialect != Dialect.QUARTZ) {
                    return "%s step must be positive in %s".formatted(field.name(), element);
                }
                if (increment > field.max() && dialect != Dialect.JDK && field.kind() != Kind.DAY_OF_MONTH && field.kind() != Kind.MONTH && field.kind() != Kind.DAY_OF_WEEK) {
                    return "%s step %s is greater than %d".formatted(field.name(), step, field.max());
                }
            }
        }
        return null;
    }

    /**
     * @return {@link Integer} value, {@link String} error, or {@code null} when the value is not understood
     */
    private static Object parseValue(Dialect dialect, Field field, String token) {
        if (Character.isDigit(token.charAt(0))) {
            var value = token.length() > 9 ? Long.MAX_VALUE : Long.parseLong(token);
            // Quartz uses 98 and 99 internally for '?' and '*' and accepts them in every field
            if (dialect == Dialect.QUARTZ && (value == 98 || value == 99)) {
                return null;
            }
            if (value < field.min() || value > field.max()) {
                return "%s value %s is out of range %d-%d".formatted(field.name(), token, field.min(), field.max());
            }
            if (dialect == Dialect.JDK && field.kind() == Kind.DAY_OF_WEEK && value == 0) {
                return 1;
            }
            return (int) value;
        }
        var named = field.names().get(token);
        if (named != null) {
            return named;
        }
        var dayField = field.kind() == Kind.DAY_OF_MONTH || field.kind() == Kind.DAY_OF_WEEK;
        if (dialect != Dialect.JDK && dayField && (token.indexOf('L') >= 0 || token.indexOf('W') >= 0 || token.indexOf('C') >= 0)) {
            return null;
        }
        if (dialect == Dialect.QUARTZ && !field.names().isEmpty()) {
            // Quartz reads only the first three letters of a name, so longer words and modifiers may be valid
            return null;
        }
        return "%s value %s is not supported".formatted(field.name(), token);
    }

    /**
     * The JDK scheduler rejects every value with L, W, # or C modifiers unless the value is a month or day name.
     */
    private static String jdkModifier(Field field, String element) {
        for (var value : element.split("[-/]")) {
            if (field.names().containsKey(value)) {
                continue;
            }
            for (var modifier : new char[]{'L', 'W', '#', 'C'}) {
                if (value.indexOf(modifier) >= 0) {
                    return "%s modifier %s in %s is not supported by the JDK scheduler".formatted(field.name(), modifier, element);
                }
            }
        }
        return null;
    }

    private static boolean rejectsReversedRange(Dialect dialect, Field field) {
        return switch (dialect) {
            case JDK -> true;
            case QUARTZ -> false;
            case DB -> field.kind() == Kind.SECOND || field.kind() == Kind.MINUTE || field.kind() == Kind.HOUR;
        };
    }

    private static boolean quartzSpecialValue(String value) {
        return value.contains("98") || value.contains("99");
    }
}

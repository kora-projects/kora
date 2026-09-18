package io.koraframework.logging.logback;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Reads Logback bootstrap configuration, which happens before the application config is available.
 * <p>
 * Every property is looked up as a system property first and then as an environment variable, where the environment
 * variable name is the property in upper case with {@code .} and {@code -} replaced by {@code _}, so
 * {@code kora.logging.encoder} is also read from {@code KORA_LOGGING_ENCODER}.
 */
public final class KoraLogbackProperties {

    /** Set by Gradle on every test worker JVM, holding the worker id. */
    public static final String GRADLE_TEST_WORKER_PROPERTY = "org.gradle.test.worker";

    private KoraLogbackProperties() { }

    @Nullable
    public static String get(String property) {
        var value = System.getProperty(property);
        if (value == null) {
            value = System.getenv(asEnvironmentVariable(property));
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String get(String property, String defaultValue) {
        var value = get(property);
        return value == null ? defaultValue : value;
    }

    /**
     * Reads an integer, falling back to the default rather than failing: a mistyped value must not keep logging from
     * starting, so it is reported through {@code warn} instead.
     *
     * @param min smallest accepted value
     * @param warn receives a message explaining why a value set for the property was ignored
     */
    public static int getInt(String property, int defaultValue, int min, Consumer<String> warn) {
        var value = get(property);
        if (value == null) {
            return defaultValue;
        }
        try {
            var parsed = Integer.parseInt(value);
            if (parsed >= min) {
                return parsed;
            }
        } catch (NumberFormatException e) {
            // reported below together with values out of range
        }
        warn.accept(property + "=" + value + " is not an integer of at least " + min + ", using " + defaultValue);
        return defaultValue;
    }

    /**
     * Reads a duration in the formats Kora configuration accepts: ISO 8601 such as {@code PT1S}, or a number with a
     * unit such as {@code 1s}, {@code 500ms} or {@code 1.5m}, a number without a unit being milliseconds. Units are
     * {@code ns}, {@code us}, {@code ms}, {@code s}, {@code m}, {@code h} and {@code d}, or their long names.
     *
     * @param warn receives a message explaining why a value set for the property was ignored
     */
    public static Duration getDuration(String property, Duration defaultValue, Consumer<String> warn) {
        var value = get(property);
        if (value == null) {
            return defaultValue;
        }
        var parsed = parseDuration(value);
        if (parsed != null && !parsed.isNegative()) {
            return parsed;
        }
        warn.accept(property + "=" + value + " is not a duration such as 1s, 500ms or PT1S, using " + defaultValue);
        return defaultValue;
    }

    @Nullable
    static Duration parseDuration(String value) {
        try {
            return Duration.parse(value);
        } catch (DateTimeParseException e) {
            // not ISO 8601, try a number with a unit
        }

        var unitStart = value.length();
        while (unitStart > 0 && Character.isLetter(value.charAt(unitStart - 1))) {
            unitStart--;
        }
        var number = value.substring(0, unitStart).trim();
        var unit = value.substring(unitStart);
        if (number.isEmpty()) {
            return null;
        }
        if (unit.length() > 2 && !unit.endsWith("s")) {
            unit = unit + "s";
        }
        // deliberately case sensitive, as in Kora configuration, so m stays minutes and never months
        var timeUnit = switch (unit) {
            case "", "ms", "millis", "milliseconds" -> TimeUnit.MILLISECONDS;
            case "us", "micros", "microseconds" -> TimeUnit.MICROSECONDS;
            case "ns", "nanos", "nanoseconds" -> TimeUnit.NANOSECONDS;
            case "s", "seconds" -> TimeUnit.SECONDS;
            case "m", "minutes" -> TimeUnit.MINUTES;
            case "h", "hours" -> TimeUnit.HOURS;
            case "d", "days" -> TimeUnit.DAYS;
            default -> null;
        };
        if (timeUnit == null) {
            return null;
        }
        try {
            if (number.matches("[+-]?[0-9]+")) {
                return Duration.ofNanos(timeUnit.toNanos(Long.parseLong(number)));
            }
            return Duration.ofNanos((long) (Double.parseDouble(number) * timeUnit.toNanos(1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Reads {@code true} or {@code false}, case insensitively, falling back to the default otherwise.
     *
     * @param warn receives a message explaining why a value set for the property was ignored
     */
    public static boolean getBoolean(String property, boolean defaultValue, Consumer<String> warn) {
        var value = get(property);
        if (value == null) {
            return defaultValue;
        }
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        warn.accept(property + "=" + value + " is neither true nor false, using " + defaultValue);
        return defaultValue;
    }

    /**
     * @return whether the JVM was started by a Gradle test worker, which sets {@value #GRADLE_TEST_WORKER_PROPERTY}
     */
    public static boolean isRunningInTests() {
        return System.getProperty(GRADLE_TEST_WORKER_PROPERTY) != null;
    }

    public static String asEnvironmentVariable(String property) {
        return property.toUpperCase(Locale.ROOT)
            .replace('.', '_')
            .replace('-', '_');
    }
}

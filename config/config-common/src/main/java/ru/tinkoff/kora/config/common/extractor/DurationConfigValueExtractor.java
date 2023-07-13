package ru.tinkoff.kora.config.common.extractor;

import ru.tinkoff.kora.config.common.ConfigValue;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class DurationConfigValueExtractor implements ConfigValueExtractor<Duration> {
    @Override
    @Nullable
    public Duration extract(ConfigValue<?> value) {
        if (value instanceof ConfigValue.NullValue nv) {
            return null;
        }
        if (value instanceof ConfigValue.NumberValue number) {
            return Duration.ofMillis(number.value().longValue());
        }
        if (!(value instanceof ConfigValue.StringValue str)) {
            throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.StringValue.class);
        }
        try {
            var nanos = parseDuration(str);
            return Duration.ofNanos(nanos);
        } catch (Exception e) {
            throw ConfigValueExtractionException.parsingError(value, e);
        }
    }

    public static long parseDuration(ConfigValue<String> configValue) {
        var s = ConfigImplUtil.unicodeTrim(configValue.value());
        var originalUnitString = getUnits(s);
        var unitString = originalUnitString;
        var numberString = ConfigImplUtil.unicodeTrim(s.substring(0, s.length()
                                                                     - unitString.length()));

        // this would be caught later anyway, but the error message
        // is more helpful if we check it here.
        if (numberString.length() == 0)
            throw new ConfigValueExtractionException(configValue.origin().config(), "No number in duration value " + configValue.origin().path(), null);

        if (unitString.length() > 2 && !unitString.endsWith("s"))
            unitString = unitString + "s";

        // note that this is deliberately case-sensitive
        var units = switch (unitString) {
            case "", "ms", "millis", "milliseconds" -> TimeUnit.MILLISECONDS;
            case "us", "micros", "microseconds" -> TimeUnit.MICROSECONDS;
            case "ns", "nanos", "nanoseconds" -> TimeUnit.NANOSECONDS;
            case "d", "days" -> TimeUnit.DAYS;
            case "h", "hours" -> TimeUnit.HOURS;
            case "s", "seconds" -> TimeUnit.SECONDS;
            case "m", "minutes" -> TimeUnit.MINUTES;
            default -> throw new ConfigValueExtractionException(
                configValue.origin().config(),
                "Could not parse time unit '%s' (try ns, us, ms, s, m, h, d) in %s".formatted(originalUnitString, configValue.origin().path()),
                null
            );
        };

        try {
            // if the string is purely digits, parse as an integer to avoid
            // possible precision loss;
            // otherwise as a double.
            if (numberString.matches("[+-]?[0-9]+")) {
                return units.toNanos(Long.parseLong(numberString));
            } else {
                long nanosInUnit = units.toNanos(1);
                return (long) (Double.parseDouble(numberString) * nanosInUnit);
            }
        } catch (NumberFormatException e) {
            throw new ConfigValueExtractionException(configValue.origin().config(), "Could not parse duration number %s in %s".formatted(numberString, configValue.origin().path()), null);
        }
    }

    private static String getUnits(String s) {
        int i = s.length() - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (!Character.isLetter(c))
                break;
            i -= 1;
        }
        return s.substring(i + 1);
    }
}

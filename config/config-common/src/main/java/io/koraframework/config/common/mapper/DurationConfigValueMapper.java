package io.koraframework.config.common.mapper;

import io.koraframework.config.common.*;
import io.koraframework.config.common.exception.*;
import io.koraframework.config.common.util.*;
import org.jspecify.annotations.*;

import java.time.*;
import java.util.concurrent.*;

public class DurationConfigValueMapper implements ConfigValueMapper<Duration> {

    @Nullable
    @Override
    public Duration map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        if (value instanceof ConfigValue.NumberValue number) {
            return Duration.ofMillis(number.value().longValue());
        }
        if (!(value instanceof ConfigValue.StringValue str)) {
            throw ConfigValueException.unexpectedValueType(value, ConfigValue.StringValue.class);
        }
        try {
            return parseString(str);
        } catch (Exception e) {
            throw ConfigValueException.parsingError(value, e);
        }
    }

    private static Duration parseString(ConfigValue<String> configValue) {
        String raw = configValue.value();
        if (raw == null || raw.isEmpty()) {
            throw new ConfigValueException(configValue.origin().config(), "Empty duration value in " + configValue.origin().path(), null);
        }

        char firstChar = raw.charAt(0);
        if (firstChar == 'P' || firstChar == 'p' || (raw.length() > 1 && (raw.charAt(1) == 'P' || raw.charAt(1) == 'p'))) {
            try {
                return Duration.parse(raw);
            } catch (Exception e) {
            }
        }

        return parseHoconStyle(configValue);
    }

    private static Duration parseHoconStyle(ConfigValue<String> configValue) {
        var nanos = parseDuration(configValue);
        return Duration.ofNanos(nanos);
    }

    private static long parseDuration(ConfigValue<String> configValue) {
        var s = ConfigValueMapperUtils.unicodeTrim(configValue.value());
        var originalUnitString = getUnits(s);
        var unitString = originalUnitString;
        var numberString = ConfigValueMapperUtils.unicodeTrim(s.substring(0, s.length() - unitString.length()));

        if (numberString.length() == 0) {
            throw new ConfigValueException(configValue.origin().config(), "No number in duration value " + configValue.origin().path(), null);
        }

        if (unitString.length() > 2 && !unitString.endsWith("s")) {
            unitString = unitString + "s";
        }

        TimeUnit units;
        if (unitString.isEmpty() || unitString.equals("ms") || unitString.equals("millis") || unitString.equals("milliseconds")) {
            units = TimeUnit.MILLISECONDS;
        } else if (unitString.equals("us") || unitString.equals("micros") || unitString.equals("microseconds")) {
            units = TimeUnit.MICROSECONDS;
        } else if (unitString.equals("ns") || unitString.equals("nanos") || unitString.equals("nanoseconds")) {
            units = TimeUnit.NANOSECONDS;
        } else if (unitString.equals("d") || unitString.equals("days")) {
            units = TimeUnit.DAYS;
        } else if (unitString.equals("h") || unitString.equals("hours")) {
            units = TimeUnit.HOURS;
        } else if (unitString.equals("s") || unitString.equals("seconds")) {
            units = TimeUnit.SECONDS;
        } else if (unitString.equals("m") || unitString.equals("minutes")) {
            units = TimeUnit.MINUTES;
        } else {
            throw new ConfigValueException(
                configValue.origin().config(),
                "Could not parse time unit '%s' (try ns, us, ms, s, m, h, d) in %s".formatted(originalUnitString, configValue.origin().path()),
                null
            );
        }

        try {
            if (isPureInteger(numberString)) {
                return units.toNanos(Long.parseLong(numberString));
            } else {
                long nanosInUnit = units.toNanos(1);
                return (long) (Double.parseDouble(numberString) * nanosInUnit);
            }
        } catch (NumberFormatException e) {
            throw new ConfigValueException(configValue.origin().config(), "Could not parse duration number %s in %s".formatted(numberString, configValue.origin().path()), null);
        }
    }

    private static boolean isPureInteger(String str) {
        int len = str.length();
        if (len == 0) return false;
        int i = 0;
        char first = str.charAt(0);
        if (first == '-' || first == '+') {
            i++;
            if (len == 1) return false;
        }
        for (; i < len; i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static String getUnits(String s) {
        int i = s.length() - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (!Character.isLetter(c)) {break;}
            i -= 1;
        }
        return s.substring(i + 1);
    }
}

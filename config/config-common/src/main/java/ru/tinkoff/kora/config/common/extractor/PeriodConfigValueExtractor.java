package ru.tinkoff.kora.config.common.extractor;

import ru.tinkoff.kora.config.common.ConfigValue;

import java.time.DateTimeException;
import java.time.Period;
import java.time.temporal.ChronoUnit;

public class PeriodConfigValueExtractor implements ConfigValueExtractor<Period> {
    @Override
    public Period extract(ConfigValue<?> value) {
        if (value instanceof ConfigValue.NumberValue number) {
            return Period.ofDays(number.value().intValue());
        }
        if (!(value instanceof ConfigValue.StringValue str)) {
            throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.StringValue.class);
        }
        try {
            return parsePeriod(str);
        } catch (Exception e) {
            throw ConfigValueExtractionException.parsingError(value, e);
        }

    }

    public static Period parsePeriod(ConfigValue<String> configValue) {
        var s = ConfigImplUtil.unicodeTrim(configValue.value());
        var originalUnitString = getUnits(s);
        var unitString = originalUnitString;
        var numberString = ConfigImplUtil.unicodeTrim(s.substring(0, s.length() - unitString.length()));

        // this would be caught later anyway, but the error message
        // is more helpful if we check it here.
        if (numberString.length() == 0)
            throw new ConfigValueExtractionException(configValue.origin().config(), "No number in period value " + configValue.origin().path(), null);

        if (unitString.length() > 2 && !unitString.endsWith("s"))
            unitString = unitString + "s";

        // note that this is deliberately case-sensitive
        var units = switch (unitString) {
            case "", "d", "days" -> ChronoUnit.DAYS;
            case "w", "weeks" -> ChronoUnit.WEEKS;
            case "m", "mo", "months" -> ChronoUnit.MONTHS;
            case "y", "years" -> ChronoUnit.YEARS;

            default -> throw new ConfigValueExtractionException(
                configValue.origin().config(),
                "Could not parse time unit '%s' (try d, w, mo, y) in %s".formatted(originalUnitString, configValue.origin().path()),
                null
            );
        };

        try {
            return periodOf(Integer.parseInt(numberString), units);
        } catch (NumberFormatException e) {
            throw new ConfigValueExtractionException(configValue.origin().config(), "Could not parse period number %s in %s".formatted(numberString, configValue.origin().path()), null);
        }
    }

    private static Period periodOf(int n, ChronoUnit unit) {
        if (unit.isTimeBased()) {
            throw new DateTimeException(unit + " cannot be converted to a java.time.Period");
        }

        return switch (unit) {
            case DAYS -> Period.ofDays(n);
            case WEEKS -> Period.ofWeeks(n);
            case MONTHS -> Period.ofMonths(n);
            case YEARS -> Period.ofYears(n);
            default -> throw new DateTimeException(unit + " cannot be converted to a java.time.Period");
        };
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

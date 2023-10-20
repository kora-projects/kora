package ru.tinkoff.kora.config.common.extractor;

import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.origin.ConfigOrigin;

import java.util.function.Function;

public class ConfigValueExtractionException extends RuntimeException {
    private final ConfigOrigin origin;

    public ConfigValueExtractionException(ConfigOrigin origin, String message, Throwable cause) {
        super(message, cause);
        this.origin = origin;
    }

    public ConfigOrigin getOrigin() {
        return origin;
    }

    public static <T> T handle(ConfigValue<?> value, Function<ConfigValue<?>, T> thunk) {
        try {
            return thunk.apply(value);
        } catch (ConfigValueExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw ConfigValueExtractionException.parsingError(value, e);
        }
    }

    public static ConfigValueExtractionException unexpectedValueType(ConfigValue<?> value, Class<? extends ConfigValue<?>> expectedType) {
        var message = String.format(
            "Config expected value with type '%s' but received type '%s' and value '%s' at path '%s' for origin '%s'",
            expectedType.getSimpleName(),
            value.getClass().getSimpleName(),
            value.value(),
            value.origin().path(),
            value.origin().config().description()
        );

        return new ConfigValueExtractionException(value.origin().config(), message, null);
    }

    public static ConfigValueExtractionException parsingError(ConfigValue<?> value, Exception error) {
        var message = String.format(
            "Config value parsing failed with '%s' for value '%s' at path: '%s' for origin '%s'",
            error.getMessage(),
            value.value(),
            value.origin().path(),
            value.origin().config().description()
        );

        return new ConfigValueExtractionException(value.origin().config(), message, error);
    }

    public static ConfigValueExtractionException missingValue(ConfigValue<?> value) {
        var message = String.format(
            "Config expected value, but got null at path: '%s' for origin '%s'",
            value.origin().path(),
            value.origin().config().description()
        );

        return new ConfigValueExtractionException(value.origin().config(), message, null);
    }

    public static ConfigValueExtractionException missingValueAfterParse(ConfigValue<?> value) {
        var message = String.format(
            "Config expected value, but got null after parsing at path: '%s' for origin '%s'",
            value.origin().path(),
            value.origin().config().description()
        );

        return new ConfigValueExtractionException(value.origin().config(), message, null);
    }
}

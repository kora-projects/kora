package io.koraframework.config.common.exception;

import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.origin.ConfigOrigin;

import java.util.function.Function;

public class ConfigValueException extends RuntimeException {

    private final ConfigOrigin origin;

    public ConfigValueException(ConfigOrigin origin, String message) {
        super(message);
        this.origin = origin;
    }

    public ConfigValueException(ConfigOrigin origin, String message, Throwable cause) {
        super(message, cause);
        this.origin = origin;
    }

    public ConfigOrigin getOrigin() {
        return origin;
    }

    public static <T> T handle(ConfigValue<?> value, Function<ConfigValue<?>, T> thunk) {
        try {
            return thunk.apply(value);
        } catch (ConfigValueException e) {
            throw e;
        } catch (Exception e) {
            throw ConfigValueException.parsingError(value, e);
        }
    }

    public static ConfigValueException unexpectedValueType(ConfigValue<?> value, Class<? extends ConfigValue<?>> expectedType) {
        var message = String.format(
            "Config expected value with type '%s' but received type '%s' and value '%s' at path '%s' for origin '%s'",
            expectedType.getSimpleName(),
            value.getClass().getSimpleName(),
            value.value(),
            value.origin().path(),
            value.origin().config().description()
        );

        return new ConfigValueException(value.origin().config(), message, null);
    }

    public static ConfigValueException parsingError(ConfigValue<?> value, Exception error) {
        var message = String.format(
            "Config value parsing failed with '%s' for value '%s' at path: '%s' for origin '%s'",
            error.getMessage(),
            value.value(),
            value.origin().path(),
            value.origin().config().description()
        );

        return new ConfigValueException(value.origin().config(), message, error);
    }

    public static ConfigValueException missingValue(ConfigValue<?> value) {
        var message = String.format(
            "Config expected value, but got null at path: '%s' for origin '%s'",
            value.origin().path(),
            value.origin().config().description()
        );

        return new ConfigValueException(value.origin().config(), message, null);
    }

    public static ConfigValueException missingValueAfterParse(ConfigValue<?> value) {
        var message = String.format(
            "Config expected value, but got null after parsing at path: '%s' for origin '%s'",
            value.origin().path(),
            value.origin().config().description()
        );

        return new ConfigValueException(value.origin().config(), message, null);
    }
}

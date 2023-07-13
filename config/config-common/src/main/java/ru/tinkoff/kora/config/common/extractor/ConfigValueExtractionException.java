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
            "Expected %s value type but %s found, origin '%s', path '%s'",
            expectedType,
            value.getClass(),
            value.origin().config().description(),
            value.origin().path()
        );

        return new ConfigValueExtractionException(value.origin().config(), message, null);
    }

    public static ConfigValueExtractionException parsingError(ConfigValue<?> value, Exception error) {
        var message = String.format(
            "Parameter parsing error, origin '%s', message: %s, path: '%s'",
            value.origin().config().description(),
            error.getMessage(),
            value.origin().path()
        );

        return new ConfigValueExtractionException(value.origin().config(), message, error);
    }

    public static ConfigValueExtractionException missingValue(ConfigValue<?> value) {
        var message = String.format(
            "Expected value, but got null, origin '%s', path: '%s'",
            value.origin().config().description(),
            value.origin().path()
        );

        return new ConfigValueExtractionException(value.origin().config(), message, null);
    }
    public static ConfigValueExtractionException missingValueAfterParse(ConfigValue<?> value) {
        var message = String.format(
            "Expected value, but got null after parsing, origin '%s', path: '%s'",
            value.origin().config().description(),
            value.origin().path()
        );

        return new ConfigValueExtractionException(value.origin().config(), message, null);
    }
}

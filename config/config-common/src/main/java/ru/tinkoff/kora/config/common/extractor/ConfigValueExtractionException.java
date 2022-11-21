package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

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

  public static <T> T handle(ConfigValue value, Function<ConfigValue, T> thunk) {
    try {
      return thunk.apply(value);
    } catch (ConfigValueExtractionException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigValueExtractionException.parsingError(value, e);
    }
  }

  public static ConfigValueExtractionException unexpectedValueType(ConfigValue value, ConfigValueType expectedType) {
    String message = String.format(
            "Expected %s value type but %s found, origin '%s'",
            expectedType,
            value.valueType(),
            value.origin().description()
    );

    return new ConfigValueExtractionException(value.origin(), message, null);
  }

  public static ConfigValueExtractionException parsingError(ConfigValue value, Exception error) {
    String message = String.format(
            "Parameter parsing error, origin '%s', message: %s",
            value.origin().description(),
            error.getMessage()
    );

    return new ConfigValueExtractionException(value.origin(), message, error);
  }
}

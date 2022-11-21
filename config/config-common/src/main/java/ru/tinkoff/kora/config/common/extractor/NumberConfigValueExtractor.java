package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import java.math.BigDecimal;

public final class NumberConfigValueExtractor implements ConfigValueExtractor<BigDecimal> {
  @Override
  public BigDecimal extract(ConfigValue value) {
    switch (value.valueType()) {
      case NUMBER -> {
        return new BigDecimal(value.unwrapped().toString());
      }
      case STRING -> {
        try {
          return new BigDecimal(((String) value.unwrapped()));
        } catch (NumberFormatException ignored) {
          //fallback to unexpectedValueType
        }
      }
    }

    throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValueType.NUMBER);
  }
}

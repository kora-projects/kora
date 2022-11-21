package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

public final class BooleanConfigValueExtractor implements ConfigValueExtractor<Boolean> {
  @Override
  public Boolean extract(ConfigValue value) {
    switch (value.valueType()) {
      case BOOLEAN -> {
        return (Boolean) value.unwrapped();
      }
      case STRING -> {
        String stringValue = value.unwrapped().toString();
        if (stringValue.equals("true")) {
          return Boolean.TRUE;
        } else if (stringValue.equals("false")) {
          return Boolean.FALSE;
        }
      }
    }

    throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValueType.BOOLEAN);
  }
}

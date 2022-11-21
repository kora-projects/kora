package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

public abstract class ObjectConfigValueExtractor<T> implements ConfigValueExtractor<T> {
  @Override
  public T extract(ConfigValue value) {
    if (value.valueType() != ConfigValueType.OBJECT) {
      throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValueType.OBJECT);
    }

    return ConfigValueExtractionException.handle(value, (v) -> extract(((ConfigObject) v).toConfig()));
  }

  protected abstract T extract(Config config);
}

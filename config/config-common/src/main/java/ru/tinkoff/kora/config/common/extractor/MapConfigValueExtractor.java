package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MapConfigValueExtractor<T> implements ConfigValueExtractor<Map<String, T>> {
  private final ConfigValueExtractor<T> mapValueExtractor;

  public MapConfigValueExtractor(ConfigValueExtractor<T> mapValueExtractor) {
    this.mapValueExtractor = mapValueExtractor;
  }

  @Override
  public Map<String, T> extract(ConfigValue value) {
    switch (value.valueType()) {
      case NULL -> {
        return null;
      }
      case OBJECT -> {
        var configObject = ((ConfigObject) value);
        var result = new LinkedHashMap<String, T>(configObject.size());
        for (Map.Entry<String, ConfigValue> entry : configObject.entrySet()) {
          result.put(entry.getKey(), mapValueExtractor.extract(entry.getValue()));
        }

        return result;
      }
    }

    throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValueType.OBJECT);
  }
}

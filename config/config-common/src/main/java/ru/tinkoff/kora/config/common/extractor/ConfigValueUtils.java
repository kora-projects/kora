package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

public class ConfigValueUtils {
  public static ConfigValue getValueOrNull(Config config, String path) {
    return config.hasPath(path)
        ? config.getValue(path)
        : config.withValue(path, ConfigValueFactory.fromAnyRef(null)).getValue(path);
  }
}

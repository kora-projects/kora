package ru.tinkoff.kora.config.common.extractor;

import ru.tinkoff.kora.config.common.ConfigValue;

import java.util.ArrayList;
import java.util.Properties;

public class PropertiesConfigValueExtractor implements ConfigValueExtractor<Properties> {
    @Override
    public Properties extract(ConfigValue<?> value) {
        if (!(value instanceof ConfigValue.ObjectValue objectValue)) {
            throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.ObjectValue.class);
        }
        var accumulator = new Properties();

        for (var entry : objectValue) {
            collectAllPaths("", entry.getKey(), entry.getValue(), accumulator);
        }
        return accumulator;
    }

    private void collectAllPaths(String previousPath, String key, ConfigValue<?> value, Properties accumulator) {
        if (value instanceof ConfigValue.ObjectValue objectValue) {
            for (var entry : objectValue) {
                collectAllPaths(previousPath + key + ".", entry.getKey(), entry.getValue(), accumulator);
            }
        } else if (value instanceof ConfigValue.ArrayValue arrayValue) {
            var res = new ArrayList<>();
            for (var configValue : arrayValue) {
                if (!(configValue instanceof ConfigValue.ObjectValue) && !(configValue instanceof ConfigValue.ArrayValue)) {
                    res.add(configValue.value());
                }
            }
            accumulator.put(previousPath + key, res);
        } else {
            accumulator.put(previousPath + key, value.value());
        }
    }
}

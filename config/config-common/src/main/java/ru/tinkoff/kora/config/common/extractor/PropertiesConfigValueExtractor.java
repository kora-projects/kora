package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

public class PropertiesConfigValueExtractor implements ConfigValueExtractor<Properties> {
    @Override
    public Properties extract(ConfigValue value) {
        if (value.valueType() != ConfigValueType.OBJECT) {
            throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValueType.OBJECT);
        }
        var configObject = ((ConfigObject) value);
        var accumulator = new Properties();

        for (Map.Entry<String, ConfigValue> entry : configObject.entrySet()) {
            collectAllPaths("", entry.getKey(), entry.getValue(), accumulator);
        }
        return accumulator;
    }

    private void collectAllPaths(String previousPath, String key, ConfigValue value, Properties accumulator) {
        switch (value.valueType()) {
            case OBJECT -> {
                var map = (ConfigObject) value;
                for (var entry : map.entrySet()) {
                    collectAllPaths(previousPath + key + ".", entry.getKey(), entry.getValue(), accumulator);
                }
            }
            case LIST -> {
                var list = ((ConfigList) value);
                var res = new ArrayList<>();
                for (ConfigValue configValue : list) {
                    ConfigValueType configValueType = configValue.valueType();
                    if (configValueType != ConfigValueType.OBJECT && configValueType != ConfigValueType.LIST) {
                        res.add(configValue.unwrapped());
                    }
                }
                accumulator.put(previousPath + key, res);
            }
            default -> {
                accumulator.put(previousPath + key, value.unwrapped());
            }
        }
    }
}

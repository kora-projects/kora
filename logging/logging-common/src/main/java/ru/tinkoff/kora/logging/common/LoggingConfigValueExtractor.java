package ru.tinkoff.kora.logging.common;

import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LoggingConfigValueExtractor implements ConfigValueExtractor<LoggingConfig> {
    @Override
    public LoggingConfig extract(ConfigValue value) {
        if (value.valueType() != ConfigValueType.OBJECT) {
            throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValueType.OBJECT);
        }

        var levels = new LinkedHashMap<String, String>();

        var configObject = ((ConfigObject) value);
        var levelsObject = configObject.get("levels");
        if (levelsObject == null) {
            levelsObject = configObject.get("level");
        }

        if (levelsObject != null) {
            if (levelsObject.valueType() != ConfigValueType.OBJECT) {
                throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValueType.OBJECT);
            }

            for (Map.Entry<String, ConfigValue> entry : ((ConfigObject) levelsObject).entrySet()) {
                collectLevels("", entry.getKey(), entry.getValue(), levels);
            }
        }

        return new LoggingConfig(levels);
    }

    private void collectLevels(String previousPath, String key, ConfigValue value, Map<String, String> accumulator) {
        if (value.valueType() == ConfigValueType.STRING) {
            accumulator.put(previousPath + key, (String) value.unwrapped());
        } else if (value.valueType() == ConfigValueType.OBJECT) {
            var map = (ConfigObject) value;
            for (var entry : map.entrySet()) {
                collectLevels(previousPath + key + ".", entry.getKey(), entry.getValue(), accumulator);
            }
        }
    }
}

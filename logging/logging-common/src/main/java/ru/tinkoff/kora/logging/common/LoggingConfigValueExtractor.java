package ru.tinkoff.kora.logging.common;

import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.ConfigValue.ObjectValue;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractionException;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LoggingConfigValueExtractor implements ConfigValueExtractor<LoggingConfig> {
    @Override
    public LoggingConfig extract(ConfigValue<?> value) {
        if (value instanceof ConfigValue.NullValue) {
            return new LoggingConfig(Map.of("ROOT", "info"));
        }
        if (!(value instanceof ObjectValue objectValue)) {
            throw ConfigValueExtractionException.unexpectedValueType(value, ObjectValue.class);
        }

        var levels = new LinkedHashMap<String, String>();

        var levelsObject = objectValue.get("levels");
        if (levelsObject instanceof ConfigValue.NullValue) {
            levelsObject = objectValue.get("level");
        }

        if (levelsObject instanceof ConfigValue.NullValue) {
            return new LoggingConfig(levels);
        }
        if (levelsObject instanceof ObjectValue l) {
            for (var entry : l) {
                collectLevels("", entry.getKey(), entry.getValue(), levels);
            }
            return new LoggingConfig(levels);
        } else {
            throw ConfigValueExtractionException.unexpectedValueType(value, ObjectValue.class);
        }
    }

    private void collectLevels(String previousPath, String key, ConfigValue<?> value, Map<String, String> accumulator) {
        if (value instanceof ConfigValue.StringValue str) {
            accumulator.put(previousPath + key, str.value());
        } else if (value instanceof ObjectValue objectValue) {
            for (var entry : objectValue) {
                collectLevels(previousPath + key + ".", entry.getKey(), entry.getValue(), accumulator);
            }
        }
    }
}

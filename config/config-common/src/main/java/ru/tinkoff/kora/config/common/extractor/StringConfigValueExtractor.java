package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

public final class StringConfigValueExtractor implements ConfigValueExtractor<String> {
    @Override
    public String extract(ConfigValue value) {
        return switch (value.valueType()) {
            case NUMBER, BOOLEAN, STRING -> value.unwrapped().toString();
            default -> throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValueType.STRING);
        };
    }
}

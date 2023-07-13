package ru.tinkoff.kora.config.common.extractor;

import ru.tinkoff.kora.config.common.ConfigValue;

public final class StringConfigValueExtractor implements ConfigValueExtractor<String> {
    @Override
    public String extract(ConfigValue<?> value) {
        if (value instanceof ConfigValue.NumberValue numberValue) {
            return numberValue.value().toString();
        }
        if (value instanceof ConfigValue.BooleanValue booleanValue) {
            return booleanValue.value().toString();
        }
        if (value instanceof ConfigValue.StringValue stringValue) {
            return stringValue.value();
        }
        throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.StringValue.class);
    }
}

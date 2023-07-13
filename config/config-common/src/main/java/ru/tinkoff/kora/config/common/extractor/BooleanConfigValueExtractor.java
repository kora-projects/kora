package ru.tinkoff.kora.config.common.extractor;

import ru.tinkoff.kora.config.common.ConfigValue;

public final class BooleanConfigValueExtractor implements ConfigValueExtractor<Boolean> {
    @Override
    public Boolean extract(ConfigValue<?> value) {
        if (value instanceof ConfigValue.BooleanValue booleanValue) {
            return booleanValue.value();
        }
        if (value instanceof ConfigValue.StringValue str) {
            var stringValue = str.value();
            if (stringValue.equals("true")) {
                return Boolean.TRUE;
            } else if (stringValue.equals("false")) {
                return Boolean.FALSE;
            }
        }
        throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.BooleanValue.class);
    }
}

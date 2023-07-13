package ru.tinkoff.kora.config.common.extractor;

import ru.tinkoff.kora.config.common.ConfigValue;

import java.util.UUID;

public class UUIDConfigValueExtractor implements ConfigValueExtractor<UUID> {

    @Override
    public UUID extract(ConfigValue<?> value) {
        if (value instanceof ConfigValue.StringValue stringValue) {
            return UUID.fromString(stringValue.value());
        }
        throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.StringValue.class);
    }

}

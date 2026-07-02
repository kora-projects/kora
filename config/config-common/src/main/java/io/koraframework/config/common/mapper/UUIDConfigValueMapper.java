package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.util.UUID;

public class UUIDConfigValueMapper implements ConfigValueMapper<UUID> {

    @Nullable
    @Override
    public UUID map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        if (value instanceof ConfigValue.StringValue stringValue) {
            return UUID.fromString(stringValue.value());
        } else {
            throw ConfigValueException.unexpectedValueType(value, ConfigValue.StringValue.class);
        }
    }
}

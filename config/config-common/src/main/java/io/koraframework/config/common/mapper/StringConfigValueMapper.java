package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

public final class StringConfigValueMapper implements ConfigValueMapper<String> {
    @Nullable
    @Override
    public String map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        if (value instanceof ConfigValue.NumberValue numberValue) {
            return numberValue.value().toString();
        } else if (value instanceof ConfigValue.BooleanValue booleanValue) {
            return booleanValue.value().toString();
        } else if (value instanceof ConfigValue.StringValue stringValue) {
            return stringValue.value();
        } else {
            throw ConfigValueException.unexpectedValueType(value, ConfigValue.StringValue.class);
        }
    }
}

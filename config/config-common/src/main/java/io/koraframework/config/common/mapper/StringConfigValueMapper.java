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

        return switch (value) {
            case ConfigValue.NumberValue numberValue -> numberValue.value().toString();
            case ConfigValue.BooleanValue booleanValue -> booleanValue.value().toString();
            case ConfigValue.StringValue stringValue -> stringValue.value();
            default -> throw ConfigValueException.unexpectedValueType(value, ConfigValue.StringValue.class);
        };
    }
}

package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;
import io.koraframework.common.util.Size;
import io.koraframework.config.common.ConfigValue;

public final class SizeConfigValueMapper implements ConfigValueMapper<Size> {

    @Nullable
    @Override
    public Size map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        return switch (value) {
            case ConfigValue.NumberValue number -> Size.ofBytesBinary(number.value().longValue());
            case ConfigValue.StringValue string -> Size.parse(string.value());
            default -> throw ConfigValueException.unexpectedValueType(value, ConfigValue.StringValue.class);
        };
    }
}

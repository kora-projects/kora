package io.koraframework.config.common.mapper;

import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;

public final class BooleanConfigValueMapper implements ConfigValueMapper<Boolean> {

    @Nullable
    @Override
    public Boolean map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

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
        throw ConfigValueException.unexpectedValueType(value, ConfigValue.BooleanValue.class);
    }
}

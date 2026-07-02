package io.koraframework.config.common.mapper;

import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

public final class NumberConfigValueMapper implements ConfigValueMapper<BigDecimal> {

    @Nullable
    @Override
    public BigDecimal map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        if (value instanceof ConfigValue.NumberValue numberValue) {
            return new BigDecimal(numberValue.value().toString());
        } else if (value instanceof ConfigValue.StringValue stringValue) {
            try {
                return new BigDecimal(stringValue.value());
            } catch (NumberFormatException ignored) {
                //fallback to unexpectedValueType
            }
        }
        throw ConfigValueException.unexpectedValueType(value, ConfigValue.NumberValue.class);
    }
}

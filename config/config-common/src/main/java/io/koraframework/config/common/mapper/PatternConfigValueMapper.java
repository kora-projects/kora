package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.util.regex.Pattern;

public class PatternConfigValueMapper implements ConfigValueMapper<Pattern> {

    @Nullable
    @Override
    public Pattern map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        if (value instanceof ConfigValue.StringValue stringValue) {
            return Pattern.compile(stringValue.value());
        } else {
            throw ConfigValueException.unexpectedValueType(value, ConfigValue.StringValue.class);
        }
    }
}

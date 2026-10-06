package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.time.LocalTime;

public class LocalTimeConfigValueMapper implements ConfigValueMapper<LocalTime> {

    @Nullable
    @Override
    public LocalTime map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        return ConfigValueException.handle(value, v -> LocalTime.parse(v.asString()));
    }
}

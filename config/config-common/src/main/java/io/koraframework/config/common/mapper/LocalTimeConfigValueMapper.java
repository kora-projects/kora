package io.koraframework.config.common.mapper;

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
        return LocalTime.parse(value.asString());
    }
}

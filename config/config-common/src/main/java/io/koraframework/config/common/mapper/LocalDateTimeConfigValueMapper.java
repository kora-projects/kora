package io.koraframework.config.common.mapper;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.time.LocalDateTime;

public class LocalDateTimeConfigValueMapper implements ConfigValueMapper<LocalDateTime> {

    @Nullable
    @Override
    public LocalDateTime map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        return LocalDateTime.parse(value.asString());
    }
}

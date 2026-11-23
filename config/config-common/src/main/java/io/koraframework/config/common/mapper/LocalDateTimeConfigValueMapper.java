package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
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
        return ConfigValueException.handle(value, v -> LocalDateTime.parse(v.asString()));
    }
}

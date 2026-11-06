package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.time.LocalDate;

public class LocalDateConfigValueMapper implements ConfigValueMapper<LocalDate> {

    @Nullable
    @Override
    public LocalDate map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        return ConfigValueException.handle(value, v -> LocalDate.parse(v.asString()));
    }
}

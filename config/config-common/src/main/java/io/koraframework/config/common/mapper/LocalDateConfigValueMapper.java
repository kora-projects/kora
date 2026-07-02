package io.koraframework.config.common.mapper;

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
        return LocalDate.parse(value.asString());
    }
}

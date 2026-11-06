package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.time.OffsetDateTime;

public class OffsetDateTimeConfigValueMapper implements ConfigValueMapper<OffsetDateTime> {

    @Nullable
    @Override
    public OffsetDateTime map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        return ConfigValueException.handle(value, v -> OffsetDateTime.parse(v.asString()));
    }
}

package io.koraframework.config.common.mapper;

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

        return OffsetDateTime.parse(value.asString());
    }
}

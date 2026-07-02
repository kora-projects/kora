package io.koraframework.config.common.mapper;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.time.OffsetTime;

public class OffsetTimeConfigValueMapper implements ConfigValueMapper<OffsetTime> {

    @Nullable
    @Override
    public OffsetTime map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        return OffsetTime.parse(value.asString());
    }
}

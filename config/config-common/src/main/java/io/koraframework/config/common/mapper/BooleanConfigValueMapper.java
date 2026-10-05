package io.koraframework.config.common.mapper;

import io.koraframework.config.common.ConfigValue;
import org.jspecify.annotations.Nullable;

public final class BooleanConfigValueMapper implements ConfigValueMapper<Boolean> {

    @Nullable
    @Override
    public Boolean map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        return value.asBoolean();
    }
}

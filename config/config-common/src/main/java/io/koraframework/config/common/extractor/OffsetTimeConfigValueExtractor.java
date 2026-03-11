package io.koraframework.config.common.extractor;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.time.OffsetTime;

public class OffsetTimeConfigValueExtractor implements ConfigValueExtractor<OffsetTime> {

    @Override
    @Nullable
    public OffsetTime extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        return OffsetTime.parse(value.asString());
    }

}

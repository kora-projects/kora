package ru.tinkoff.kora.config.common.extractor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

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

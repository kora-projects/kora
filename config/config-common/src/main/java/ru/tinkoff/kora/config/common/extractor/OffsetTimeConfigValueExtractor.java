package ru.tinkoff.kora.config.common.extractor;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.time.OffsetTime;

public class OffsetTimeConfigValueExtractor implements ConfigValueExtractor<OffsetTime> {

    @Nullable
    @Override
    public OffsetTime extract(ConfigValue<?> value) {
        return OffsetTime.parse(value.asString());
    }

}

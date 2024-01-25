package ru.tinkoff.kora.config.common.extractor;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.time.LocalTime;

public class LocalTimeConfigValueExtractor implements ConfigValueExtractor<LocalTime> {

    @Nullable
    @Override
    public LocalTime extract(ConfigValue<?> value) {
        return LocalTime.parse(value.asString());
    }

}

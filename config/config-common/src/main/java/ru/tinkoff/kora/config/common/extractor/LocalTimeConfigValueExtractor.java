package ru.tinkoff.kora.config.common.extractor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.time.LocalTime;

public class LocalTimeConfigValueExtractor implements ConfigValueExtractor<LocalTime> {

    @Override
    @Nullable
    public LocalTime extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        return LocalTime.parse(value.asString());
    }

}

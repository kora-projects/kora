package ru.tinkoff.kora.config.common.extractor;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.time.LocalDateTime;

public class LocalDateTimeConfigValueExtractor implements ConfigValueExtractor<LocalDateTime> {

    @Nullable
    @Override
    public LocalDateTime extract(ConfigValue<?> value) {
        return LocalDateTime.parse(value.asString());
    }

}

package ru.tinkoff.kora.config.common.extractor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.time.LocalDateTime;

public class LocalDateTimeConfigValueExtractor implements ConfigValueExtractor<LocalDateTime> {

    @Override
    @Nullable
    public LocalDateTime extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        return LocalDateTime.parse(value.asString());
    }

}

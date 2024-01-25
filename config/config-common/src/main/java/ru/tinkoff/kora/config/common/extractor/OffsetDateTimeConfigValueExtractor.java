package ru.tinkoff.kora.config.common.extractor;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.time.OffsetDateTime;

public class OffsetDateTimeConfigValueExtractor implements ConfigValueExtractor<OffsetDateTime> {

    @Nullable
    @Override
    public OffsetDateTime extract(ConfigValue<?> value) {
        return OffsetDateTime.parse(value.asString());
    }

}

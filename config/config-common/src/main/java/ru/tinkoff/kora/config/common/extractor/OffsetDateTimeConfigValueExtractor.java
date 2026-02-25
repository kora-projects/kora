package ru.tinkoff.kora.config.common.extractor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.time.OffsetDateTime;

public class OffsetDateTimeConfigValueExtractor implements ConfigValueExtractor<OffsetDateTime> {

    @Override
    @Nullable
    public OffsetDateTime extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        return OffsetDateTime.parse(value.asString());
    }

}

package ru.tinkoff.kora.config.common.extractor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.time.LocalDate;

public class LocalDateConfigValueExtractor implements ConfigValueExtractor<LocalDate> {

    @Override
    @Nullable
    public LocalDate extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        return LocalDate.parse(value.asString());
    }

}

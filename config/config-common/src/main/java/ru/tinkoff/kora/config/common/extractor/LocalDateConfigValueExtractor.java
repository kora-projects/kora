package ru.tinkoff.kora.config.common.extractor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.time.LocalDate;

public class LocalDateConfigValueExtractor implements ConfigValueExtractor<LocalDate> {

    @Nullable
    @Override
    public LocalDate extract(ConfigValue<?> value) {
        return LocalDate.parse(value.asString());
    }

}

package ru.tinkoff.kora.config.common.extractor;

import ru.tinkoff.kora.config.common.ConfigValue;

import java.util.function.Function;

public final class ConfigValueExtractorMapping<T, U> implements ConfigValueExtractor<U> {
    private final ConfigValueExtractor<T> baseExtractor;
    private final Function<T, U> mapping;

    public ConfigValueExtractorMapping(ConfigValueExtractor<T> baseExtractor, Function<T, U> mapping) {
        this.baseExtractor = baseExtractor;
        this.mapping = mapping;
    }

    @Override
    public U extract(ConfigValue<?> value) {
        return mapping.apply(baseExtractor.extract(value));
    }
}

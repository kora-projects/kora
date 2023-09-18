package ru.tinkoff.kora.config.common.extractor;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.util.function.Function;

@FunctionalInterface
public interface ConfigValueExtractor<T> extends Mapping.MappingFunction {
    @Nullable
    T extract(ConfigValue<?> value);

    default <U> ConfigValueExtractor<U> map(Function<T, U> f) {
        return new ConfigValueExtractorMapping<>(this, f);
    }
}

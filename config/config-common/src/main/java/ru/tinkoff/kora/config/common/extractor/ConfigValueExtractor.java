package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.ConfigValue;

import java.util.function.Function;

@FunctionalInterface
public interface ConfigValueExtractor<T> {
    T extract(ConfigValue value);

    default <U> ConfigValueExtractor<U> map(Function<T, U> f) {
        return new ConfigValueExtractorMapping<>(this, f);
    }
}

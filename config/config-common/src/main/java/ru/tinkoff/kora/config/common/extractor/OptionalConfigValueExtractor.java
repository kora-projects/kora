package ru.tinkoff.kora.config.common.extractor;

import ru.tinkoff.kora.config.common.ConfigValue;

import java.util.Optional;

public final class OptionalConfigValueExtractor<T> implements ConfigValueExtractor<Optional<T>> {
    private final ConfigValueExtractor<T> delegate;

    public OptionalConfigValueExtractor(ConfigValueExtractor<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<T> extract(ConfigValue<?> value) {
        if (value == null || value instanceof ConfigValue.NullValue) {
            return Optional.empty();
        }
        var parsed = this.delegate.extract(value);
        return Optional.ofNullable(parsed);
    }
}

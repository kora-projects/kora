package io.koraframework.config.common.mapper;

import io.koraframework.config.common.ConfigValue;

import java.util.Optional;

public final class OptionalConfigValueMapper<T> implements ConfigValueMapper<Optional<T>> {

    private final ConfigValueMapper<T> delegate;

    public OptionalConfigValueMapper(ConfigValueMapper<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<T> map(ConfigValue<?> value) {
        if (value.isNull()) {
            return Optional.empty();
        }
        var parsed = this.delegate.map(value);
        return Optional.ofNullable(parsed);
    }
}

package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.time.Duration;
import java.util.Objects;

public class DurationArrayConfigValueMapper implements ConfigValueMapper<Duration[]> {

    private final ConfigValueMapper<Duration> durationConfigValueMapper;

    public DurationArrayConfigValueMapper(ConfigValueMapper<Duration> doubleConfigValueMapper) {
        this.durationConfigValueMapper = doubleConfigValueMapper;
    }

    @Override
    public Duration @Nullable [] map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        var array = value.asArray();
        var result = new Duration[array.value().size()];
        for (int i = 0; i < result.length; i++) {
            var item = array.value().get(i);
            if (item.isNull()) {
                throw ConfigValueException.unexpectedValueType(item, ConfigValue.NumberValue.class);
            }
            result[i] = Objects.requireNonNull(this.durationConfigValueMapper.map(item));
        }
        return result;
    }
}

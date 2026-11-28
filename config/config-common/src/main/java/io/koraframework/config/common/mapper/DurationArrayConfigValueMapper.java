package io.koraframework.config.common.mapper;

import io.koraframework.config.common.*;
import io.koraframework.config.common.exception.*;
import org.jspecify.annotations.*;

import java.time.*;
import java.util.*;

public class DurationArrayConfigValueMapper implements ConfigValueMapper<Duration[]> {

    private final ConfigValueMapper<Duration> durationConfigValueMapper;

    public DurationArrayConfigValueMapper(ConfigValueMapper<Duration> doubleConfigValueMapper) {
        this.durationConfigValueMapper = doubleConfigValueMapper;
    }

    @Override
    public Duration @Nullable [] map(ConfigValue<?> value) {
        if (value instanceof ConfigValue.NullValue) {
            return null;
        }
        if (!(value instanceof ConfigValue.ArrayValue)) {
            throw ConfigValueException.unexpectedValueType(value, ConfigValue.ArrayValue.class);
        }
        var list = ((ConfigValue.ArrayValue) value).value();
        var result = new Duration[list.size()];
        for (int i = 0; i < result.length; i++) {
            var item = list.get(i);
            if (item instanceof ConfigValue.NullValue) {
                throw ConfigValueException.unexpectedValueType(item, ConfigValue.NumberValue.class);
            }
            result[i] = Objects.requireNonNull(this.durationConfigValueMapper.map(item));
        }
        return result;
    }
}

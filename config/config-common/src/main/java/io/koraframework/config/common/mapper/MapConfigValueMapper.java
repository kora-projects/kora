package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.koraframework.config.common.ConfigValue.ObjectValue;

public final class MapConfigValueMapper<T> implements ConfigValueMapper<Map<String, @Nullable T>> {
    private final ConfigValueMapper<T> mapValueMapper;

    public MapConfigValueMapper(ConfigValueMapper<T> mapValueMapper) {
        this.mapValueMapper = mapValueMapper;
    }

    @Nullable
    @Override
    public Map<String, @Nullable T> map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        if (value instanceof ObjectValue objectValue) {
            var result = new LinkedHashMap<String, @Nullable T>(objectValue.value().size());
            for (var entry : objectValue) {
                result.put(entry.getKey(), mapValueMapper.map(entry.getValue()));
            }

            return result;
        }
        throw ConfigValueException.unexpectedValueType(value, ObjectValue.class);
    }
}

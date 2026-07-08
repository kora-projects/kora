package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.koraframework.config.common.ConfigValue.ObjectValue;

public final class MapConfigKeyValueMapper<K, V> implements ConfigValueMapper<Map<K, @Nullable V>> {

    private final ConfigValueMapper<K> mapKeyExtractor;
    private final ConfigValueMapper<V> mapValueMapper;

    public MapConfigKeyValueMapper(ConfigValueMapper<K> mapKeyExtractor,
                                   ConfigValueMapper<V> mapValueMapper) {
        this.mapKeyExtractor = mapKeyExtractor;
        this.mapValueMapper = mapValueMapper;
    }

    @Override
    public Map<K, V> map(ConfigValue<?> configValue) {
        if (configValue instanceof ObjectValue objectValue) {
            var result = new LinkedHashMap<K, @Nullable V>(objectValue.value().size());
            for (var entry : objectValue) {
                K key = mapKeyExtractor.map(new ConfigValue.StringValue(entry.getValue().origin(), entry.getKey()));
                V value = mapValueMapper.map(entry.getValue());
                result.put(key, value);
            }

            return result;
        }
        throw ConfigValueException.unexpectedValueType(configValue, ObjectValue.class);
    }
}

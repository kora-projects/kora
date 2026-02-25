package ru.tinkoff.kora.config.common.extractor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.util.LinkedHashMap;
import java.util.Map;

import static ru.tinkoff.kora.config.common.ConfigValue.ObjectValue;

public final class MapConfigKeyValueExtractor<K, V> implements ConfigValueExtractor<Map<K, @Nullable V>> {

    private final ConfigValueExtractor<K> mapKeyExtractor;
    private final ConfigValueExtractor<V> mapValueExtractor;

    public MapConfigKeyValueExtractor(ConfigValueExtractor<K> mapKeyExtractor,
                                      ConfigValueExtractor<V> mapValueExtractor) {
        this.mapKeyExtractor = mapKeyExtractor;
        this.mapValueExtractor = mapValueExtractor;
    }

    @Override
    public Map<K, V> extract(ConfigValue<?> configValue) {
        if (configValue instanceof ObjectValue objectValue) {
            var result = new LinkedHashMap<K, @Nullable V>(objectValue.value().size());
            for (var entry : objectValue) {
                K key = mapKeyExtractor.extract(new ConfigValue.StringValue(entry.getValue().origin(), entry.getKey()));
                V value = mapValueExtractor.extract(entry.getValue());
                result.put(key, value);
            }

            return result;
        }
        throw ConfigValueExtractionException.unexpectedValueType(configValue, ObjectValue.class);
    }
}

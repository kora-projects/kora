package ru.tinkoff.kora.config.common.extractor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.util.LinkedHashMap;
import java.util.Map;

import static ru.tinkoff.kora.config.common.ConfigValue.ObjectValue;

public final class MapConfigValueExtractor<T> implements ConfigValueExtractor<Map<String, @Nullable T>> {
    private final ConfigValueExtractor<T> mapValueExtractor;

    public MapConfigValueExtractor(ConfigValueExtractor<T> mapValueExtractor) {
        this.mapValueExtractor = mapValueExtractor;
    }

    @Override
    @Nullable
    public Map<String, @Nullable T> extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        if (value instanceof ObjectValue objectValue) {
            var result = new LinkedHashMap<String, @Nullable T>(objectValue.value().size());
            for (var entry : objectValue) {
                result.put(entry.getKey(), mapValueExtractor.extract(entry.getValue()));
            }

            return result;
        }
        throw ConfigValueExtractionException.unexpectedValueType(value, ObjectValue.class);
    }
}

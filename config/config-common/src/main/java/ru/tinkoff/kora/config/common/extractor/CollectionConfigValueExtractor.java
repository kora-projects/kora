package ru.tinkoff.kora.config.common.extractor;


import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.util.Collection;

public abstract class CollectionConfigValueExtractor<T, C extends Collection<@Nullable T>> implements ConfigValueExtractor<C> {

    private final ConfigValueExtractor<T> elementValueExtractor;

    protected CollectionConfigValueExtractor(ConfigValueExtractor<T> elementValueExtractor) {
        this.elementValueExtractor = elementValueExtractor;
    }

    @Override
    @Nullable
    public C extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        if (value instanceof ConfigValue.StringValue(var origin, var value1)) {
            if (value1.isEmpty()) {
                return newCollection(0);
            }

            var values = value1.split(",");
            var result = newCollection(values.length);
            for (var stringValue : values) {
                var listValue = new ConfigValue.StringValue(origin, stringValue.trim());
                result.add(elementValueExtractor.extract(listValue));
            }
            return result;
        }
        if (value instanceof ConfigValue.ArrayValue array) {
            var result = newCollection(array.value().size());
            for (var element : array) {
                result.add(elementValueExtractor.extract(element));
            }
            return result;
        }
        throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.ArrayValue.class);
    }

    protected abstract C newCollection(int size);

}

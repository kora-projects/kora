package ru.tinkoff.kora.config.common.extractor;


import ru.tinkoff.kora.config.common.ConfigValue;

import java.util.Collection;

public abstract class CollectionConfigValueExtractor<T, C extends Collection<T>> implements ConfigValueExtractor<C> {

    private final ConfigValueExtractor<T> elementValueExtractor;

    protected CollectionConfigValueExtractor(ConfigValueExtractor<T> elementValueExtractor) {
        this.elementValueExtractor = elementValueExtractor;
    }

    @Override
    public C extract(ConfigValue<?> value) {
        if (value instanceof ConfigValue.StringValue str) {
            var values = str.value().split(",");
            var result = newCollection(values.length);
            for (var stringValue : values) {
                var listValue = new ConfigValue.StringValue(str.origin(), stringValue.trim());
                result.add(elementValueExtractor.extract(listValue));
            }
            return result;
        }
        if (value instanceof ConfigValue.ArrayValue array) {
            var result = newCollection(array.value().size());
            for (var element : array) {
                result.add(elementValueExtractor.extract(element));
            }
        }
        throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.ArrayValue.class);
    }

    protected abstract C newCollection(int size);

}

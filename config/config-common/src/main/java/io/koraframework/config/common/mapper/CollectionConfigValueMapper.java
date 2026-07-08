package io.koraframework.config.common.mapper;


import io.koraframework.config.common.ConfigValue;
import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

public abstract class CollectionConfigValueMapper<T, C extends Collection<@Nullable T>> implements ConfigValueMapper<C> {

    private final ConfigValueMapper<T> elementValueMapper;

    protected CollectionConfigValueMapper(ConfigValueMapper<T> elementValueMapper) {
        this.elementValueMapper = elementValueMapper;
    }

    @Nullable
    @Override
    public C map(ConfigValue<?> value) {
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
                result.add(elementValueMapper.map(listValue));
            }
            return result;
        } else if (value instanceof ConfigValue.ArrayValue array) {
            var result = newCollection(array.value().size());
            for (var element : array) {
                result.add(elementValueMapper.map(element));
            }
            return result;
        } else {
            throw ConfigValueException.unexpectedValueType(value, ConfigValue.ArrayValue.class);
        }
    }

    protected abstract C newCollection(int size);

}

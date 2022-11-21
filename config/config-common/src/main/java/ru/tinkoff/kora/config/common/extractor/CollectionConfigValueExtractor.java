package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.ConfigValueType;

import java.util.Collection;

public abstract class CollectionConfigValueExtractor<T, C extends Collection<T>> implements ConfigValueExtractor<C> {

    private final ConfigValueExtractor<T> elementValueExtractor;

    protected CollectionConfigValueExtractor(ConfigValueExtractor<T> elementValueExtractor) {
        this.elementValueExtractor = elementValueExtractor;
    }

    @Override
    public C extract(ConfigValue value) {
        return switch (value.valueType()) {
            case NULL -> null;
            case STRING -> {
                var values = ((String) value.unwrapped()).split(",");
                var result = newCollection(values.length);
                for (String stringValue : values) {
                    var listValue = ConfigValueFactory.fromAnyRef(stringValue).withOrigin(value.origin());
                    result.add(elementValueExtractor.extract(listValue));
                }

                yield result;
            }
            case LIST -> {
                var configList = ((ConfigList) value);
                var result = newCollection(configList.size());
                for (ConfigValue configValue : configList) {
                    result.add(elementValueExtractor.extract(configValue));
                }
                yield result;
            }
            default -> throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValueType.LIST);
        };
    }

    protected abstract C newCollection(int size);

}

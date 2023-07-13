package ru.tinkoff.kora.config.common.extractor;

import ru.tinkoff.kora.config.common.ConfigValue;

import java.util.HashMap;

public class EnumConfigValueExtractor<T extends Enum<T>> implements ConfigValueExtractor<T> {
    private final HashMap<String, T> map;

    public EnumConfigValueExtractor(Class<T> type) {
        this.map = new HashMap<>();
        for (T enumConstant : type.getEnumConstants()) {
            this.map.put(enumConstant.name(), enumConstant);
        }
    }

    @Override
    public T extract(ConfigValue<?> value) {
        if (value instanceof ConfigValue.StringValue stringValue) {
            var str = stringValue.value();
            var enumValue = this.map.get(str);
            if (enumValue == null) {
                throw ConfigValueExtractionException.parsingError(value, new IllegalArgumentException("Unknown enum value: " + str));
            }
            return enumValue;
        }

        throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.StringValue.class);
    }
}

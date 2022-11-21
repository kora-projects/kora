package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

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
    public T extract(ConfigValue value) {
        if (value.valueType() == ConfigValueType.STRING) {
            var str = value.unwrapped().toString();
            var enumValue = this.map.get(str);
            if (enumValue == null) {
                throw ConfigValueExtractionException.parsingError(value, new IllegalArgumentException("Unknown enum value: " + str));
            }
            return enumValue;
        }

        throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValueType.STRING);
    }
}

package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.util.HashMap;

public class EnumConfigValueMapper<T extends Enum<T>> implements ConfigValueMapper<T> {

    private final HashMap<String, T> map;

    public EnumConfigValueMapper(Class<T> type) {
        this.map = new HashMap<>();
        for (T enumConstant : type.getEnumConstants()) {
            this.map.put(enumConstant.toString(), enumConstant);
        }
    }

    @Nullable
    @Override
    public T map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        if (value instanceof ConfigValue.StringValue stringValue) {
            var str = stringValue.value();
            var enumValue = this.map.get(str);
            if (enumValue == null) {
                throw ConfigValueException.parsingError(value, new IllegalArgumentException("Unknown enum value: " + str + " when expected one of " + map.keySet()));
            }
            return enumValue;
        }
        throw ConfigValueException.unexpectedValueType(value, ConfigValue.StringValue.class);
    }
}

package ru.tinkoff.kora.config.common.extractor;

import ru.tinkoff.kora.common.util.Size;
import ru.tinkoff.kora.config.common.ConfigValue;

public final class SizeConfigValueExtractor implements ConfigValueExtractor<Size> {

    @Override
    public Size extract(ConfigValue<?> value) {
        if (value instanceof ConfigValue.NullValue nv) {
            return null;
        } else if (value instanceof ConfigValue.NumberValue number) {
            return Size.ofBytesBinary(number.value().longValue());
        } else if (value instanceof ConfigValue.StringValue string) {
            return Size.parse(string.value());
        }

        throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.StringValue.class);
    }
}

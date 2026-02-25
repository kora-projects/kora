package ru.tinkoff.kora.config.common.extractor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.util.Size;
import ru.tinkoff.kora.config.common.ConfigValue;

public final class SizeConfigValueExtractor implements ConfigValueExtractor<Size> {

    @Override
    @Nullable
    public Size extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        } else if (value instanceof ConfigValue.NumberValue number) {
            return Size.ofBytesBinary(number.value().longValue());
        } else if (value instanceof ConfigValue.StringValue string) {
            return Size.parse(string.value());
        }

        throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.StringValue.class);
    }
}

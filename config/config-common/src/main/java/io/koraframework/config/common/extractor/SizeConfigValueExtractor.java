package io.koraframework.config.common.extractor;

import org.jspecify.annotations.Nullable;
import io.koraframework.common.util.Size;
import io.koraframework.config.common.ConfigValue;

public final class SizeConfigValueExtractor implements ConfigValueExtractor<Size> {

    @Nullable
    @Override
    public Size extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        return switch (value) {
            case ConfigValue.NumberValue number -> Size.ofBytesBinary(number.value().longValue());
            case ConfigValue.StringValue string -> Size.parse(string.value());
            default -> throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.StringValue.class);
        };
    }
}

package io.koraframework.config.common.extractor;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

public final class StringConfigValueExtractor implements ConfigValueExtractor<String> {
    @Nullable
    @Override
    public String extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        return switch (value) {
            case ConfigValue.NumberValue numberValue -> numberValue.value().toString();
            case ConfigValue.BooleanValue booleanValue -> booleanValue.value().toString();
            case ConfigValue.StringValue stringValue -> stringValue.value();
            default -> throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.StringValue.class);
        };
    }
}

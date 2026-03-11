package io.koraframework.config.common.extractor;

import io.koraframework.config.common.ConfigValue;
import org.jspecify.annotations.Nullable;

public final class BooleanConfigValueExtractor implements ConfigValueExtractor<Boolean> {
    @Override
    @Nullable
    public Boolean extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        if (value instanceof ConfigValue.BooleanValue booleanValue) {
            return booleanValue.value();
        }
        if (value instanceof ConfigValue.StringValue str) {
            var stringValue = str.value();
            if (stringValue.equals("true")) {
                return Boolean.TRUE;
            } else if (stringValue.equals("false")) {
                return Boolean.FALSE;
            }
        }
        throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.BooleanValue.class);
    }
}

package io.koraframework.config.common.extractor;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.util.regex.Pattern;

public class PatternConfigValueExtractor implements ConfigValueExtractor<Pattern> {
    @Override
    @Nullable
    public Pattern extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        if (value instanceof ConfigValue.StringValue stringValue) {
            return Pattern.compile(stringValue.value());
        } else {
            throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.StringValue.class);
        }
    }

}

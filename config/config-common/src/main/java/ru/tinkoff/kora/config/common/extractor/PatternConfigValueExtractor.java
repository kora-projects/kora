package ru.tinkoff.kora.config.common.extractor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

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

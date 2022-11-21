package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import java.util.regex.Pattern;

public class PatternConfigValueExtractor implements ConfigValueExtractor<Pattern> {
    @Override
    public Pattern extract(ConfigValue value) {
        if (value.valueType() == ConfigValueType.STRING) {
            return Pattern.compile(value.unwrapped().toString());
        } else {
            throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValueType.STRING);
        }
    }

}

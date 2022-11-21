package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import java.time.Duration;

public class DurationConfigValueExtractor implements ConfigValueExtractor<Duration> {
    @Override
    public Duration extract(ConfigValue value) {
        try {
            return value.atKey("fake-duration-key").getDuration("fake-duration-key");
        } catch (ConfigException.WrongType wrongType) {
            throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValueType.STRING);
        } catch (Exception e) {
            throw ConfigValueExtractionException.parsingError(value, e);
        }
    }
}

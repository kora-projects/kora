package ru.tinkoff.kora.config.common.extractor;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import java.time.Period;

public class PeriodConfigValueExtractor implements ConfigValueExtractor<Period> {
    @Override
    public Period extract(ConfigValue value) {
        try {
            return value.atKey("fake-period-key").getPeriod("fake-period-key");
        } catch (ConfigException.WrongType wrongType) {
            throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValueType.STRING);
        } catch (Exception e) {
            throw ConfigValueExtractionException.parsingError(value, e);
        }
    }
}

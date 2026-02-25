package ru.tinkoff.kora.config.common.extractor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.math.BigDecimal;

public final class NumberConfigValueExtractor implements ConfigValueExtractor<BigDecimal> {
    @Override
    @Nullable
    public BigDecimal extract(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }
        if (value instanceof ConfigValue.NumberValue numberValue) {
            return new BigDecimal(numberValue.value().toString());
        }
        if (value instanceof ConfigValue.StringValue stringValue) {
            try {
                return new BigDecimal(stringValue.value());
            } catch (NumberFormatException ignored) {
                //fallback to unexpectedValueType
            }
        }
        throw ConfigValueExtractionException.unexpectedValueType(value, ConfigValue.NumberValue.class);
    }
}

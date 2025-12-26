package ru.tinkoff.kora.config.common.extractor;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.config.common.ConfigValue;

import java.time.Duration;
import java.util.Objects;

public class DurationArrayConfigValueExtractor implements ConfigValueExtractor<Duration[]> {
    private final ConfigValueExtractor<Duration> durationConfigValueExtractor;

    public DurationArrayConfigValueExtractor(ConfigValueExtractor<Duration> doubleConfigValueExtractor) {
        this.durationConfigValueExtractor = doubleConfigValueExtractor;
    }

    @Nullable
    @Override
    public Duration[] extract(ConfigValue<?> value) {
        var array = value.asArray();
        var result = new Duration[array.value().size()];
        for (int i = 0; i < result.length; i++) {
            var item = array.value().get(i);
            if (item.isNull()) {
                throw ConfigValueExtractionException.unexpectedValueType(item, ConfigValue.NumberValue.class);
            }
            result[i] = Objects.requireNonNull(this.durationConfigValueExtractor.extract(item));
        }
        return result;
    }
}

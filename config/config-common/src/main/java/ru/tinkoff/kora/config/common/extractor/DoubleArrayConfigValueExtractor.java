package ru.tinkoff.kora.config.common.extractor;

import ru.tinkoff.kora.config.common.ConfigValue;

import javax.annotation.Nullable;
import java.util.Objects;

public class DoubleArrayConfigValueExtractor implements ConfigValueExtractor<double[]> {
    private final ConfigValueExtractor<Double> doubleConfigValueExtractor;

    public DoubleArrayConfigValueExtractor(ConfigValueExtractor<Double> doubleConfigValueExtractor) {
        this.doubleConfigValueExtractor = doubleConfigValueExtractor;
    }

    @Nullable
    @Override
    public double[] extract(ConfigValue<?> value) {
        var array = value.asArray();
        var result = new double[array.value().size()];
        for (int i = 0; i < result.length; i++) {
            var item = array.value().get(i);
            if (item.isNull()) {
                throw ConfigValueExtractionException.unexpectedValueType(item, ConfigValue.NumberValue.class);
            }
            result[i] = Objects.requireNonNull(this.doubleConfigValueExtractor.extract(item));
        }
        return result;
    }
}

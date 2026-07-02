package io.koraframework.config.common.mapper;

import io.koraframework.config.common.exception.ConfigValueException;
import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.ConfigValue;

import java.util.Objects;

public class DoubleArrayConfigValueMapper implements ConfigValueMapper<double[]> {

    private final ConfigValueMapper<Double> doubleConfigValueMapper;

    public DoubleArrayConfigValueMapper(ConfigValueMapper<Double> doubleConfigValueMapper) {
        this.doubleConfigValueMapper = doubleConfigValueMapper;
    }

    @Override
    public double @Nullable [] map(ConfigValue<?> value) {
        if (value.isNull()) {
            return null;
        }

        var array = value.asArray();
        var result = new double[array.value().size()];
        for (int i = 0; i < result.length; i++) {
            var item = array.value().get(i);
            if (item.isNull()) {
                throw ConfigValueException.unexpectedValueType(item, ConfigValue.NumberValue.class);
            }
            result[i] = Objects.requireNonNull(this.doubleConfigValueMapper.map(item));
        }
        return result;
    }
}

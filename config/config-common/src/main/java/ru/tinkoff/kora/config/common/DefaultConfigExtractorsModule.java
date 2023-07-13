package ru.tinkoff.kora.config.common;

import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.common.util.Either;
import ru.tinkoff.kora.config.common.extractor.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Period;
import java.util.*;
import java.util.regex.Pattern;

public interface DefaultConfigExtractorsModule {
    default <T> ConfigValueExtractor<List<T>> listConfigValueExtractor(ConfigValueExtractor<T> elementValueExtractor) {
        return new ListConfigValueExtractor<>(elementValueExtractor);
    }

    default <T> ConfigValueExtractor<Set<T>> setConfigValueExtractor(ConfigValueExtractor<T> elementValueExtractor) {
        return new SetConfigValueExtractor<>(elementValueExtractor);
    }

    default <T> ConfigValueExtractor<Map<String, T>> mapConfigValueExtractor(ConfigValueExtractor<T> listValueExtractor) {
        return new MapConfigValueExtractor<>(listValueExtractor);
    }

    default <T extends Enum<T>> EnumConfigValueExtractor<T> enumConfigValueExtractor(TypeRef<T> typeRef) {
        return new EnumConfigValueExtractor<>(typeRef.getRawType());
    }

    default ConfigValueExtractor<String> stringConfigValueExtractor() {
        return new StringConfigValueExtractor();
    }

    default ConfigValueExtractor<Integer> integerConfigValueExtractor() {
        return new NumberConfigValueExtractor().map(BigDecimal::intValueExact);
    }

    default ConfigValueExtractor<Long> longConfigValueExtractor() {
        return new NumberConfigValueExtractor().map(BigDecimal::longValueExact);
    }

    default ConfigValueExtractor<Float> floatConfigValueExtractor() {
        return new NumberConfigValueExtractor().map(BigDecimal::floatValue);
    }

    default ConfigValueExtractor<Double> doubleConfigValueExtractor() {
        return new NumberConfigValueExtractor().map(BigDecimal::doubleValue);
    }

    default ConfigValueExtractor<Boolean> booleanConfigValueExtractor() {
        return new BooleanConfigValueExtractor();
    }

    default ConfigValueExtractor<ConfigValue.ObjectValue> subconfigConfigValueExtractor() {
        return ConfigValue::asObject;
    }

    default ConfigValueExtractor<Duration> durationConfigValueExtractor() {
        return new DurationConfigValueExtractor();
    }

    default ConfigValueExtractor<Period> periodConfigValueExtractor() {
        return new PeriodConfigValueExtractor();
    }

    default ConfigValueExtractor<Properties> propertiesConfigValueExtractor() {
        return new PropertiesConfigValueExtractor();
    }

    default ConfigValueExtractor<Pattern> patternConfigValueExtractor() {
        return new PatternConfigValueExtractor();
    }

    default <A, B> ConfigValueExtractor<Either<A, B>> eitherExtractor(ConfigValueExtractor<A> left, ConfigValueExtractor<B> right) {
        return new EitherConfigExtractor<>(left, right);
    }

    default ConfigValueExtractor<UUID> uuidConfigValueExtractor() {
        return new UUIDConfigValueExtractor();
    }

    default ConfigValueExtractor<double[]> doubleArrayConfigValueExtractor(ConfigValueExtractor<Double> doubleExtractor) {
        return new DoubleArrayConfigValueExtractor(doubleExtractor);
    }
}

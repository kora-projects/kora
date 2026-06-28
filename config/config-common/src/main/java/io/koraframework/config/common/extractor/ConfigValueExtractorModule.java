package io.koraframework.config.common.extractor;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.DefaultComponent;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.common.util.Either;
import io.koraframework.common.util.Size;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

public interface ConfigValueExtractorModule {

    @DefaultComponent
    default <T> ConfigValueExtractor<List<T>> listConfigValueExtractor(ConfigValueExtractor<T> elementValueExtractor) {
        return new ListConfigValueExtractor<>(elementValueExtractor);
    }

    @DefaultComponent
    default <T> ConfigValueExtractor<Set<T>> setConfigValueExtractor(ConfigValueExtractor<T> elementValueExtractor) {
        return new SetConfigValueExtractor<>(elementValueExtractor);
    }

    @DefaultComponent
    default <K, V> ConfigValueExtractor<Map<K, V>> mapConfigValueExtractor(ConfigValueExtractor<K> keyExtractor, ConfigValueExtractor<V> valueExtractor) {
        return new MapConfigKeyValueExtractor<>(keyExtractor, valueExtractor);
    }

    @DefaultComponent
    default <T> OptionalConfigValueExtractor<T> optionalConfigValueExtractor(ConfigValueExtractor<T> extractor) {
        return new OptionalConfigValueExtractor<>(extractor);
    }

    @DefaultComponent
    default <T extends Enum<T>> EnumConfigValueExtractor<T> enumConfigValueExtractor(TypeRef<T> typeRef) {
        return new EnumConfigValueExtractor<>(typeRef.getRawType());
    }

    @DefaultComponent
    default ConfigValueExtractor<String> stringConfigValueExtractor() {
        return new StringConfigValueExtractor();
    }

    @DefaultComponent
    default ConfigValueExtractor<Integer> integerConfigValueExtractor() {
        return new NumberConfigValueExtractor().map(BigDecimal::intValueExact);
    }

    @DefaultComponent
    default ConfigValueExtractor<Long> longConfigValueExtractor() {
        return new NumberConfigValueExtractor().map(BigDecimal::longValueExact);
    }

    @DefaultComponent
    default ConfigValueExtractor<BigInteger> bigIntegerConfigValueExtractor() {
        return new NumberConfigValueExtractor().map(BigDecimal::toBigInteger);
    }

    @DefaultComponent
    default ConfigValueExtractor<Float> floatConfigValueExtractor() {
        return new NumberConfigValueExtractor().map(BigDecimal::floatValue);
    }

    @DefaultComponent
    default ConfigValueExtractor<Double> doubleConfigValueExtractor() {
        return new NumberConfigValueExtractor().map(BigDecimal::doubleValue);
    }

    @DefaultComponent
    default ConfigValueExtractor<BigDecimal> bigDecimalConfigValueExtractor() {
        return new NumberConfigValueExtractor();
    }

    @DefaultComponent
    default ConfigValueExtractor<Boolean> booleanConfigValueExtractor() {
        return new BooleanConfigValueExtractor();
    }

    @DefaultComponent
    default ConfigValueExtractor<ConfigValue.ObjectValue> subconfigConfigValueExtractor() {
        return ConfigValue::asObject;
    }

    @DefaultComponent
    default ConfigValueExtractor<Duration> durationConfigValueExtractor() {
        return new DurationConfigValueExtractor();
    }

    @DefaultComponent
    default ConfigValueExtractor<Period> periodConfigValueExtractor() {
        return new PeriodConfigValueExtractor();
    }

    @DefaultComponent
    default ConfigValueExtractor<Properties> propertiesConfigValueExtractor() {
        return new PropertiesConfigValueExtractor();
    }

    @DefaultComponent
    default ConfigValueExtractor<Pattern> patternConfigValueExtractor() {
        return new PatternConfigValueExtractor();
    }

    @DefaultComponent
    default <A, B> ConfigValueExtractor<Either<A, B>> eitherConfigValueExtractor(ConfigValueExtractor<A> left, ConfigValueExtractor<B> right) {
        return new EitherConfigExtractor<>(left, right);
    }

    @DefaultComponent
    default ConfigValueExtractor<UUID> uuidConfigValueExtractor() {
        return new UUIDConfigValueExtractor();
    }

    @DefaultComponent
    default ConfigValueExtractor<double[]> doubleArrayConfigValueExtractor(ConfigValueExtractor<Double> doubleExtractor) {
        return new DoubleArrayConfigValueExtractor(doubleExtractor);
    }

    @DefaultComponent
    default ConfigValueExtractor<Duration[]> durationArrayConfigValueExtractor(ConfigValueExtractor<Duration> durationExtractor) {
        return new DurationArrayConfigValueExtractor(durationExtractor);
    }

    @DefaultComponent
    default ConfigValueExtractor<Size> sizeConfigValueExtractor() {
        return new SizeConfigValueExtractor();
    }

    @DefaultComponent
    default ConfigValueExtractor<LocalDate> localDateConfigValueExtractor() {
        return new LocalDateConfigValueExtractor();
    }

    @DefaultComponent
    default ConfigValueExtractor<LocalTime> localTimeConfigValueExtractor() {
        return new LocalTimeConfigValueExtractor();
    }

    @DefaultComponent
    default ConfigValueExtractor<LocalDateTime> localDateTimeConfigValueExtractor() {
        return new LocalDateTimeConfigValueExtractor();
    }

    @DefaultComponent
    default ConfigValueExtractor<OffsetTime> offsetTimeConfigValueExtractor() {
        return new OffsetTimeConfigValueExtractor();
    }

    @DefaultComponent
    default ConfigValueExtractor<OffsetDateTime> offsetDateTimeConfigValueExtractor() {
        return new OffsetDateTimeConfigValueExtractor();
    }
}

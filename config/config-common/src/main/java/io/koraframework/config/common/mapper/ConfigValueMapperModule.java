package io.koraframework.config.common.mapper;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.config.common.ConfigValue;
import io.koraframework.common.Either;
import io.koraframework.common.util.Size;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

public interface ConfigValueMapperModule {

    @DefaultComponent
    default <T> ConfigValueMapper<List<T>> listConfigValueMapper(ConfigValueMapper<T> elementValueMapper) {
        return new ListConfigValueMapper<>(elementValueMapper);
    }

    @DefaultComponent
    default <T> ConfigValueMapper<Set<T>> setConfigValueMapper(ConfigValueMapper<T> elementValueMapper) {
        return new SetConfigValueMapper<>(elementValueMapper);
    }

    @DefaultComponent
    default <K, V> ConfigValueMapper<Map<K, V>> mapConfigValueMapper(ConfigValueMapper<K> keyExtractor, ConfigValueMapper<V> valueExtractor) {
        return new MapConfigKeyValueMapper<>(keyExtractor, valueExtractor);
    }

    @DefaultComponent
    default <T> OptionalConfigValueMapper<T> optionalConfigValueMapper(ConfigValueMapper<T> mapper) {
        return new OptionalConfigValueMapper<>(mapper);
    }

    @DefaultComponent
    default <T extends Enum<T>> EnumConfigValueMapper<T> enumConfigValueMapper(TypeRef<T> typeRef) {
        return new EnumConfigValueMapper<>(typeRef.getRawType());
    }

    @DefaultComponent
    default ConfigValueMapper<String> stringConfigValueMapper() {
        return new StringConfigValueMapper();
    }

    @DefaultComponent
    default ConfigValueMapper<Integer> integerConfigValueMapper() {
        return new NumberConfigValueMapper().andThen(BigDecimal::intValueExact);
    }

    @DefaultComponent
    default ConfigValueMapper<Long> longConfigValueMapper() {
        return new NumberConfigValueMapper().andThen(BigDecimal::longValueExact);
    }

    @DefaultComponent
    default ConfigValueMapper<BigInteger> bigIntegerConfigValueMapper() {
        return new NumberConfigValueMapper().andThen(BigDecimal::toBigInteger);
    }

    @DefaultComponent
    default ConfigValueMapper<Float> floatConfigValueMapper() {
        return new NumberConfigValueMapper().andThen(BigDecimal::floatValue);
    }

    @DefaultComponent
    default ConfigValueMapper<Double> doubleConfigValueMapper() {
        return new NumberConfigValueMapper().andThen(BigDecimal::doubleValue);
    }

    @DefaultComponent
    default ConfigValueMapper<BigDecimal> bigDecimalConfigValueMapper() {
        return new NumberConfigValueMapper();
    }

    @DefaultComponent
    default ConfigValueMapper<Boolean> booleanConfigValueMapper() {
        return new BooleanConfigValueMapper();
    }

    @DefaultComponent
    default ConfigValueMapper<ConfigValue.ObjectValue> subconfigConfigValueMapper() {
        return ConfigValue::asObject;
    }

    @DefaultComponent
    default ConfigValueMapper<Duration> durationConfigValueMapper() {
        return new DurationConfigValueMapper();
    }

    @DefaultComponent
    default ConfigValueMapper<Period> periodConfigValueMapper() {
        return new PeriodConfigValueMapper();
    }

    @DefaultComponent
    default ConfigValueMapper<Properties> propertiesConfigValueMapper() {
        return new PropertiesConfigValueMapper();
    }

    @DefaultComponent
    default ConfigValueMapper<Pattern> patternConfigValueMapper() {
        return new PatternConfigValueMapper();
    }

    @DefaultComponent
    default <A, B> ConfigValueMapper<Either<A, B>> eitherConfigValueMapper(ConfigValueMapper<A> left, ConfigValueMapper<B> right) {
        return new EitherConfigMapper<>(left, right);
    }

    @DefaultComponent
    default ConfigValueMapper<UUID> uuidConfigValueMapper() {
        return new UUIDConfigValueMapper();
    }

    @DefaultComponent
    default ConfigValueMapper<double[]> doubleArrayConfigValueMapper(ConfigValueMapper<Double> doubleExtractor) {
        return new DoubleArrayConfigValueMapper(doubleExtractor);
    }

    @DefaultComponent
    default ConfigValueMapper<Duration[]> durationArrayConfigValueMapper(ConfigValueMapper<Duration> durationExtractor) {
        return new DurationArrayConfigValueMapper(durationExtractor);
    }

    @DefaultComponent
    default ConfigValueMapper<Size> sizeConfigValueMapper() {
        return new SizeConfigValueMapper();
    }

    @DefaultComponent
    default ConfigValueMapper<LocalDate> localDateConfigValueMapper() {
        return new LocalDateConfigValueMapper();
    }

    @DefaultComponent
    default ConfigValueMapper<LocalTime> localTimeConfigValueMapper() {
        return new LocalTimeConfigValueMapper();
    }

    @DefaultComponent
    default ConfigValueMapper<LocalDateTime> localDateTimeConfigValueMapper() {
        return new LocalDateTimeConfigValueMapper();
    }

    @DefaultComponent
    default ConfigValueMapper<OffsetTime> offsetTimeConfigValueMapper() {
        return new OffsetTimeConfigValueMapper();
    }

    @DefaultComponent
    default ConfigValueMapper<OffsetDateTime> offsetDateTimeConfigValueMapper() {
        return new OffsetDateTimeConfigValueMapper();
    }
}

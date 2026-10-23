package io.koraframework.openfeature.mapper;

import dev.openfeature.sdk.Value;
import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.util.Size;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.annotation.Json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.util.HashMap;
import java.util.UUID;
import java.util.regex.Pattern;

public interface OpenfeatureMapperModule {
    @DefaultComponent
    default OpenfeatureFlagMapper<Integer> openfeatureMapperInt() {
        return (features, key, fallback, context) -> features.getIntegerValue(key, fallback, context);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Boolean> openfeatureMapperBoolean() {
        return (features, key, fallback, context) -> features.getBooleanValue(key, fallback, context);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<String> openfeatureMapperString() {
        return (features, key, fallback, context) -> features.getStringValue(key, fallback, context);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Long> openfeatureMapperLong() {
        return (features, key, fallback, context) -> features.getLongValue(key, fallback, context);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Double> openfeatureMapperDouble() {
        return (features, key, fallback, context) -> features.getDoubleValue(key, fallback, context);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Float> openfeatureMapperFloat() {
        return (features, key, fallback, context) -> {
            var details = features.getDoubleDetails(key, fallback.doubleValue(), context);
            if (details.getErrorCode() != null || details.getValue() == null) {
                return fallback;
            }
            var value = details.getValue().floatValue();
            return Float.isFinite(value) ? value : fallback;
        };
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Value> openfeatureMapperValue() {
        return (features, key, fallback, context) -> features.getObjectValue(key, fallback, context);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Pattern> openfeatureMapperPattern() {
        return OpenfeatureFlagMapper.fromString(Pattern::pattern, Pattern::compile);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<BigInteger> openfeatureMapperBigInteger() {
        return OpenfeatureFlagMapper.fromString(BigInteger::toString, BigInteger::new);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<BigDecimal> openfeatureMapperBigDecimal() {
        return OpenfeatureFlagMapper.fromString(BigDecimal::toString, BigDecimal::new);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Period> openfeatureMapperPeriod() {
        return OpenfeatureFlagMapper.fromString(Period::toString, Period::parse);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Duration> openfeatureMapperDuration() {
        return OpenfeatureFlagMapper.fromString(Duration::toString, Duration::parse);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<UUID> openfeatureMapperUUID() {
        return OpenfeatureFlagMapper.fromString(UUID::toString, UUID::fromString);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<LocalDate> openfeatureMapperLocalDate() {
        return OpenfeatureFlagMapper.fromString(LocalDate::toString, LocalDate::parse);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<LocalDateTime> openfeatureMapperLocalDateTime() {
        return OpenfeatureFlagMapper.fromString(LocalDateTime::toString, LocalDateTime::parse);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<LocalTime> openfeatureMapperLocalTime() {
        return OpenfeatureFlagMapper.fromString(LocalTime::toString, LocalTime::parse);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<OffsetDateTime> openfeatureMapperOffsetDateTime() {
        return OpenfeatureFlagMapper.fromString(OffsetDateTime::toString, OffsetDateTime::parse);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<OffsetTime> openfeatureMapperOffsetTime() {
        return OpenfeatureFlagMapper.fromString(OffsetTime::toString, OffsetTime::parse);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Instant> openfeatureMapperInstant() {
        return OpenfeatureFlagMapper.fromString(Instant::toString, Instant::parse);
    }

    @DefaultComponent
    default OpenfeatureFlagMapper<Size> openfeatureMapperSize() {
        return OpenfeatureFlagMapper.fromString(Size::toString, Size::parse);
    }

    @DefaultComponent
    default <T extends Enum<T>> OpenfeatureFlagMapper<T> openfeatureMapperEnum(TypeRef<T> typeRef) {
        var values = new HashMap<String, T>();
        for (T value : typeRef.getRawType().getEnumConstants()) {
            values.put(value.name(), value);
        }
        return OpenfeatureFlagMapper.fromString(Enum::name, values::get);
    }

    /** Maps structured OBJECT flags to Kora JSON types. */
    @Json
    @DefaultComponent
    default <T> OpenfeatureFlagMapper<T> openfeatureMapperJson(JsonReader<T> reader) {
        var writer = new OpenfeatureValueJsonWriter();
        return OpenfeatureFlagMapper.fromObject(value -> reader.read(writer.toByteArray(value)));
    }
}

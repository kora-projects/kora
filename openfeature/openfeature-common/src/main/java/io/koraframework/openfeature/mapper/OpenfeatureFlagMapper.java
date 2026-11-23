package io.koraframework.openfeature.mapper;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Features;
import dev.openfeature.sdk.Value;
import io.koraframework.common.annotation.Mapping;

import java.util.Objects;
import java.util.function.Function;

/**
 * Maps one flag evaluation. Custom mappers should return defaultValue on provider or conversion errors.
 * Implementations are shared by the application graph and must be thread safe.
 */
@FunctionalInterface
public interface OpenfeatureFlagMapper<O> extends Mapping.MappingFunction {
    O map(Features features, String key, O defaultValue, EvaluationContext context);

    /** Builds a STRING mapper without parsing defaults or throwing on malformed remote values. */
    static <T> OpenfeatureFlagMapper<T> fromString(Function<T, String> format, Function<String, T> parse) {
        Objects.requireNonNull(format);
        Objects.requireNonNull(parse);
        return (features, key, fallback, context) -> {
            var defaultString = format.apply(Objects.requireNonNull(fallback));
            var details = features.getStringDetails(key, defaultString, context);
            if (details.getErrorCode() != null || details.getValue() == null
                || defaultString.equals(details.getValue())) {
                return fallback;
            }
            try {
                var value = parse.apply(details.getValue());
                return value == null ? fallback : value;
            } catch (RuntimeException e) {
                return fallback;
            }
        };
    }

    /** Builds an OBJECT mapper for direct Value conversion without a JSON serialization round trip. */
    static <T> OpenfeatureFlagMapper<T> fromObject(Function<Value, T> parse) {
        Objects.requireNonNull(parse);
        var defaultObject = new Value();
        return (features, key, fallback, context) -> {
            var details = features.getObjectDetails(key, defaultObject, context);
            if (details.getErrorCode() != null || details.getValue() == null || details.getValue().isNull()) {
                return fallback;
            }
            try {
                var value = parse.apply(details.getValue());
                return value == null ? fallback : value;
            } catch (RuntimeException e) {
                return fallback;
            }
        };
    }
}

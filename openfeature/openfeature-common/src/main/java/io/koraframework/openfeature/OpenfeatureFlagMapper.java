package io.koraframework.openfeature;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Features;
import jakarta.annotation.Nonnull;
import io.koraframework.common.Mapping;

@FunctionalInterface
public interface OpenfeatureFlagMapper<O> extends Mapping.MappingFunction {

    @Nonnull
    O map(Features value, String key, O defaultValue, EvaluationContext ctx);
}

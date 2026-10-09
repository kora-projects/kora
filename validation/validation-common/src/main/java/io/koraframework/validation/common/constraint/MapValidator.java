package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class MapValidator<K, V, M extends Map<K, V>> implements Validator<M> {

    @Nullable
    private final Validator<K> keyValidator;
    @Nullable
    private final Validator<V> valueValidator;

    MapValidator(@Nullable Validator<K> keyValidator, @Nullable Validator<V> valueValidator) {
        this.keyValidator = keyValidator;
        this.valueValidator = valueValidator;
    }

    @Override
    public List<Violation> validate(@Nullable M map, ValidationContext context) {
        if (map == null) {
            return Collections.emptyList();
        }

        final List<Violation> violations = new ArrayList<>();
        for (var entry : map.entrySet()) {
            var entryContext = context.addPath(String.valueOf(entry.getKey()));
            if (keyValidator != null) {
                violations.addAll(keyValidator.validate(entry.getKey(), entryContext));
            }
            if (valueValidator != null) {
                violations.addAll(valueValidator.validate(entry.getValue(), entryContext));
            }
            if (context.isFailFast() && !violations.isEmpty()) {
                return violations;
            }
        }
        return violations;
    }
}

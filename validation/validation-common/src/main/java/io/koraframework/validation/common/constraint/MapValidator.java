package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class MapValidator<K, V, M extends Map<? extends K, ? extends V>> implements Validator<M> {

    @Nullable
    private final Validator<? super K> keyValidator;
    @Nullable
    private final Validator<? super V> valueValidator;

    MapValidator(@Nullable Validator<? super K> keyValidator, @Nullable Validator<? super V> valueValidator) {
        this.keyValidator = keyValidator;
        this.valueValidator = valueValidator;
    }

    @Override
    public List<Violation> validate(@Nullable M map, ValidationContext context) {
        if (map == null || map.isEmpty()) {
            return Collections.emptyList();
        }

        List<Violation> violations = null;
        var hasKeyValidator = this.keyValidator != null;
        var hasValueValidator = this.valueValidator != null;

        for (var entry : map.entrySet()) {
            var key = entry.getKey();
            var path = key instanceof String str ? str : String.valueOf(key);
            var entryContext = context.addPath(path);

            if (hasKeyValidator) {
                var innerViolations = keyValidator.validate(key, entryContext);
                if (!innerViolations.isEmpty()) {
                    if (violations == null) {
                        violations = new ArrayList<>(innerViolations);
                    } else {
                        violations.addAll(innerViolations);
                    }
                }
            }

            if (hasValueValidator) {
                var innerViolations = valueValidator.validate(entry.getValue(), entryContext);
                if (!innerViolations.isEmpty()) {
                    if (violations == null) {
                        violations = new ArrayList<>(innerViolations);
                    } else {
                        violations.addAll(innerViolations);
                    }
                }
            }

            if (context.isFailFast() && violations != null && !violations.isEmpty()) {
                return violations;
            }
        }

        return violations == null ? Collections.emptyList() : violations;
    }
}

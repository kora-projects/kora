package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

final class NotEmptyMapValidator<K, V> implements Validator<Map<K, V>> {

    @Override
    public List<Violation> validate(Map<K, V> value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Should be not empty, but was null"));
        } else if (value.isEmpty()) {
            return List.of(context.violates("Should be not empty, but was empty"));
        }

        return Collections.emptyList();
    }
}

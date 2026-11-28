package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

final class NotEmptyMapValidator<K, V> implements Validator<Map<K, V>> {
    private static final String MSG_NULL = "Should be not empty, but was null";
    private static final String MSG_EMPTY = "Should be not empty, but was empty";

    @Override
    public List<Violation> validate(Map<K, V> value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(MSG_NULL));
        } else if (value.isEmpty()) {
            return List.of(context.violates(MSG_EMPTY));
        }

        return Collections.emptyList();
    }
}

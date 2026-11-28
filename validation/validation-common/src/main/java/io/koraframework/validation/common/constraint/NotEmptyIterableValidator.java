package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Collections;
import java.util.List;

final class NotEmptyIterableValidator<V, T extends Iterable<V>> implements Validator<T> {
    private static final String MSG_NULL = "Should be not empty, but was null";
    private static final String MSG_EMPTY = "Should be not empty, but was empty";

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(MSG_NULL));
        } else if (!value.iterator().hasNext()) {
            return List.of(context.violates(MSG_EMPTY));
        }

        return Collections.emptyList();
    }
}

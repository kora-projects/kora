package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Collections;
import java.util.List;

final class NotEmptyStringValidator<T extends CharSequence> implements Validator<T> {

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Should be not empty, but was null"));
        } else if (value.isEmpty()) {
            return List.of(context.violates("Should be not empty, but was empty"));
        }

        return Collections.emptyList();
    }
}

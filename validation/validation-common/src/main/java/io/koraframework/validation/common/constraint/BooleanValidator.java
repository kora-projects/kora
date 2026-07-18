package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Collections;
import java.util.List;

final class BooleanValidator implements Validator<Boolean> {

    private final boolean expected;

    BooleanValidator(boolean expected) {
        this.expected = expected;
    }

    @Override
    public List<Violation> validate(Boolean value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Should be " + expected + ", but was null"));
        } else if (value != expected) {
            return List.of(context.violates("Should be " + expected + ", but was: " + value));
        }

        return Collections.emptyList();
    }
}

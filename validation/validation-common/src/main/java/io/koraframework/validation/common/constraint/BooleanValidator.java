package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Collections;
import java.util.List;

final class BooleanValidator implements Validator<Boolean> {

    private final boolean expected;

    private final String nullViolationMessage;
    private final String mismatchViolationMessage;

    BooleanValidator(boolean expected) {
        this.expected = expected;
        this.nullViolationMessage = "Should be " + expected + ", but was null";
        this.mismatchViolationMessage = "Should be " + expected + ", but was: " + !expected;
    }

    @Override
    public List<Violation> validate(Boolean value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(nullViolationMessage));
        }

        if (value.booleanValue() != expected) {
            return List.of(context.violates(mismatchViolationMessage));
        }

        return Collections.emptyList();
    }
}

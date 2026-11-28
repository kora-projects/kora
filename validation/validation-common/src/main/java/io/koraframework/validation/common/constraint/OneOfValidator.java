package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Collections;
import java.util.List;
import java.util.Set;

final class OneOfValidator<T extends CharSequence> implements Validator<T> {

    private final Set<String> values;
    private final String nullViolationMessage;
    private final String mismatchViolationMessagePrefix;

    OneOfValidator(String[] values) {
        this.values = Set.of(values);

        var baseMessage = "Should be one of " + this.values;
        this.nullViolationMessage = baseMessage + ", but was null";
        this.mismatchViolationMessagePrefix = baseMessage + ", but was: ";
    }

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(nullViolationMessage));
        }
        if (!values.contains(value.toString())) {
            return List.of(context.violates(mismatchViolationMessagePrefix + value));
        }

        return Collections.emptyList();
    }
}

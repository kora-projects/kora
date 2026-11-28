package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

final class PatternValidator<T extends CharSequence> implements Validator<T> {

    private final Pattern pattern;

    private final String nullViolationMessage;
    private final String mismatchViolationMessagePrefix;

    PatternValidator(String pattern, int flags) {
        this.pattern = Pattern.compile(pattern, flags);

        var baseMessage = "Should match RegEx " + this.pattern;
        this.nullViolationMessage = baseMessage + " but was null";
        this.mismatchViolationMessagePrefix = baseMessage + " but was: ";
    }

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(nullViolationMessage));
        }

        if (!pattern.matcher(value).matches()) {
            return List.of(context.violates(mismatchViolationMessagePrefix + value));
        }

        return Collections.emptyList();
    }
}

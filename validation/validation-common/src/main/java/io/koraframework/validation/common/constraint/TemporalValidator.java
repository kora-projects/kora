package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

final class TemporalValidator<T extends Comparable<? super T>> implements Validator<T> {

    enum Rule {
        PAST,
        PAST_OR_PRESENT,
        FUTURE,
        FUTURE_OR_PRESENT
    }

    private final Rule rule;
    private final Supplier<T> now;

    private final String nullViolationMessage;
    private final String mismatchViolationMessagePrefix;

    TemporalValidator(Rule rule, Supplier<T> now) {
        this.rule = rule;
        this.now = now;

        String prefix;
        if (rule == Rule.PAST) {
            prefix = "Should be in the past";
        } else if (rule == Rule.PAST_OR_PRESENT) {
            prefix = "Should be in the past or present";
        } else if (rule == Rule.FUTURE) {
            prefix = "Should be in the future";
        } else if (rule == Rule.FUTURE_OR_PRESENT) {
            prefix = "Should be in the future or present";
        } else {
            prefix = "";
        }

        this.nullViolationMessage = prefix + ", but was null";
        this.mismatchViolationMessagePrefix = prefix + ", but was: ";
    }

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(nullViolationMessage));
        }

        var current = now.get();
        var compare = value.compareTo(current);
        var valid = false;

        if (rule == Rule.PAST) {
            valid = compare < 0;
        } else if (rule == Rule.PAST_OR_PRESENT) {
            valid = compare <= 0;
        } else if (rule == Rule.FUTURE) {
            valid = compare > 0;
        } else if (rule == Rule.FUTURE_OR_PRESENT) {
            valid = compare >= 0;
        }

        if (!valid) {
            return List.of(context.violates(mismatchViolationMessagePrefix + value));
        }

        return Collections.emptyList();
    }
}

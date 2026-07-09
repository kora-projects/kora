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

    TemporalValidator(Rule rule, Supplier<T> now) {
        this.rule = rule;
        this.now = now;
    }

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(messagePrefix() + ", but was null"));
        }

        var current = now.get();
        var compare = value.compareTo(current);
        var valid = switch (rule) {
            case PAST -> compare < 0;
            case PAST_OR_PRESENT -> compare <= 0;
            case FUTURE -> compare > 0;
            case FUTURE_OR_PRESENT -> compare >= 0;
        };

        if (!valid) {
            return List.of(context.violates(messagePrefix() + ", but was: " + value));
        }

        return Collections.emptyList();
    }

    private String messagePrefix() {
        return switch (rule) {
            case PAST -> "Should be in the past";
            case PAST_OR_PRESENT -> "Should be in the past or present";
            case FUTURE -> "Should be in the future";
            case FUTURE_OR_PRESENT -> "Should be in the future or present";
        };
    }
}

package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class SizeCollectionValidator<V, T extends Collection<V>> implements Validator<T> {

    private final int from;
    private final int to;

    private final String nullViolationMessage;
    private final String violationMessagePrefix;

    public SizeCollectionValidator(int from, int to) {
        if (from < 0) {
            throw new IllegalArgumentException("Invalid size range: from must be >= 0, got " + from);
        }
        if (to < from) {
            throw new IllegalArgumentException("Invalid size range: to must be >= from, got from=" + from + ", to=" + to);
        }

        this.from = from;
        this.to = to;

        var baseMessage = "Size should be in range from '" + from + "' to '" + to + "'";
        this.nullViolationMessage = baseMessage + ", but value was null";
        this.violationMessagePrefix = baseMessage + ", but was ";
    }

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(nullViolationMessage));
        }

        var size = value.size();
        if (size < from) {
            return List.of(context.violates(violationMessagePrefix + "smaller: " + size));
        } else if (size > to) {
            return List.of(context.violates(violationMessagePrefix + "greater: " + size));
        }

        return Collections.emptyList();
    }
}

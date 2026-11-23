package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;
import io.koraframework.validation.common.annotation.Range;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

final class RangeLongNumberValidator<T extends Number> implements Validator<T> {

    private final long from;
    private final long to;
    private final Range.Boundary boundary;

    private final Predicate<T> fromPredicate;
    private final Predicate<T> toPredicate;

    RangeLongNumberValidator(double fromDouble, double toDouble, Range.Boundary boundary) {
        if (toDouble < fromDouble)
            throw new IllegalArgumentException("Invalid range bounds: to must be >= from, got from=" + fromDouble + ", to=" + toDouble);

        // round fractional bounds inwards to the nearest integer that keeps the same set of valid values
        this.from = (long) switch (boundary) {
            case INCLUSIVE_INCLUSIVE, INCLUSIVE_EXCLUSIVE -> Math.ceil(fromDouble);
            case EXCLUSIVE_INCLUSIVE, EXCLUSIVE_EXCLUSIVE -> Math.floor(fromDouble);
        };
        this.to = (long) switch (boundary) {
            case INCLUSIVE_EXCLUSIVE, EXCLUSIVE_EXCLUSIVE -> Math.ceil(toDouble);
            case EXCLUSIVE_INCLUSIVE, INCLUSIVE_INCLUSIVE -> Math.floor(toDouble);
        };
        this.boundary = boundary;
        this.fromPredicate = switch (boundary) {
            case INCLUSIVE_INCLUSIVE, INCLUSIVE_EXCLUSIVE -> (v -> v.longValue() >= from);
            case EXCLUSIVE_INCLUSIVE, EXCLUSIVE_EXCLUSIVE -> (v -> v.longValue() > from);
        };

        this.toPredicate = switch (boundary) {
            case INCLUSIVE_EXCLUSIVE, EXCLUSIVE_EXCLUSIVE -> (v -> v.longValue() < to);
            case EXCLUSIVE_INCLUSIVE, INCLUSIVE_INCLUSIVE -> (v -> v.longValue() <= to);
        };
    }

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Should be in range from '" + from + "' to '" + to + "', but was null"));
        }

        if (!fromPredicate.test(value)) {
            return List.of(context.violates("Should be in range from '" + from + "' to '" + to + "', but was smaller: " + value));
        } else if (!toPredicate.test(value)) {
            return List.of(context.violates("Should be in range from '" + from + "' to '" + to + "', but was greater: " + value));
        }

        return Collections.emptyList();
    }
}

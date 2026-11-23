package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;
import io.koraframework.validation.common.annotation.Range;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

final class RangeDoubleNumberValidator<T extends Number> implements Validator<T> {

    private final double from;
    private final double to;
    private final Range.Boundary boundary;

    private final Predicate<T> fromPredicate;
    private final Predicate<T> toPredicate;

    RangeDoubleNumberValidator(double from, double to, Range.Boundary boundary) {
        if (to < from)
            throw new IllegalArgumentException("Invalid range bounds: to must be >= from, got from=" + from + ", to=" + to);

        this.from = from;
        this.to = to;
        this.boundary = boundary;
        this.fromPredicate = switch (boundary) {
            case INCLUSIVE_INCLUSIVE, INCLUSIVE_EXCLUSIVE -> (v -> toDouble(v) >= from);
            case EXCLUSIVE_INCLUSIVE, EXCLUSIVE_EXCLUSIVE -> (v -> toDouble(v) > from);
        };

        this.toPredicate = switch (boundary) {
            case INCLUSIVE_EXCLUSIVE, EXCLUSIVE_EXCLUSIVE -> (v -> toDouble(v) < to);
            case EXCLUSIVE_INCLUSIVE, INCLUSIVE_INCLUSIVE -> (v -> toDouble(v) <= to);
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

    private static double toDouble(Number value) {
        // widening a float adds binary noise (0.1f -> 0.10000000149011612), compare its decimal value instead
        return value instanceof Float ? Double.parseDouble(value.toString()) : value.doubleValue();
    }
}

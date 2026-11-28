package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;
import io.koraframework.validation.common.annotation.Range;

import java.util.Collections;
import java.util.List;

final class RangeDoubleNumberValidator<T extends Number> implements Validator<T> {

    private final double from;
    private final double to;

    private final boolean fromInclusive;
    private final boolean toInclusive;

    private final String nullViolationMessage;
    private final String smallerViolationMessagePrefix;
    private final String greaterViolationMessagePrefix;

    RangeDoubleNumberValidator(double from, double to, Range.Boundary boundary) {
        if (to < from) {
            throw new IllegalArgumentException("Invalid range bounds: to must be >= from, got from=" + from + ", to=" + to);
        }

        this.from = from;
        this.to = to;

        this.fromInclusive = boundary == Range.Boundary.INCLUSIVE_INCLUSIVE || boundary == Range.Boundary.INCLUSIVE_EXCLUSIVE;
        this.toInclusive = boundary == Range.Boundary.EXCLUSIVE_INCLUSIVE || boundary == Range.Boundary.INCLUSIVE_INCLUSIVE;

        var baseMessage = "Should be in range from '" + this.from + "' to '" + this.to + "'";
        this.nullViolationMessage = baseMessage + ", but was null";
        this.smallerViolationMessagePrefix = baseMessage + ", but was smaller: ";
        this.greaterViolationMessagePrefix = baseMessage + ", but was greater: ";
    }

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(nullViolationMessage));
        }

        var doubleValue = value.doubleValue();

        var isFromValid = fromInclusive ? doubleValue >= from : doubleValue > from;
        if (!isFromValid) {
            return List.of(context.violates(smallerViolationMessagePrefix + value));
        }

        var isToValid = toInclusive ? doubleValue <= to : doubleValue < to;
        if (!isToValid) {
            return List.of(context.violates(greaterViolationMessagePrefix + value));
        }

        return Collections.emptyList();
    }
}

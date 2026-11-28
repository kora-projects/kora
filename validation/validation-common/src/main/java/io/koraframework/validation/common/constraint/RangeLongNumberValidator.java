package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;
import io.koraframework.validation.common.annotation.Range;

import java.util.Collections;
import java.util.List;

final class RangeLongNumberValidator<T extends Number> implements Validator<T> {

    private final long from;
    private final long to;

    private final boolean fromInclusive;
    private final boolean toInclusive;

    private final String nullViolationMessage;
    private final String smallerViolationMessagePrefix;
    private final String greaterViolationMessagePrefix;

    RangeLongNumberValidator(double fromDouble, double toDouble, Range.Boundary boundary) {
        if (toDouble < fromDouble) {
            throw new IllegalArgumentException("Invalid range bounds: to must be >= from, got from=" + fromDouble + ", to=" + toDouble);
        }

        this.from = (long) fromDouble;
        this.to = (long) toDouble;

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

        var longValue = value.longValue();

        var isFromValid = fromInclusive ? longValue >= from : longValue > from;
        if (!isFromValid) {
            return List.of(context.violates(smallerViolationMessagePrefix + value));
        }

        var isToValid = toInclusive ? longValue <= to : longValue < to;
        if (!isToValid) {
            return List.of(context.violates(greaterViolationMessagePrefix + value));
        }

        return Collections.emptyList();
    }
}

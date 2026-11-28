package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;
import io.koraframework.validation.common.annotation.Range;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

final class RangeBigDecimalValidator implements Validator<BigDecimal> {

    private final BigDecimal from;
    private final BigDecimal to;

    private final boolean fromInclusive;
    private final boolean toInclusive;

    private final String nullViolationMessage;
    private final String smallerViolationMessagePrefix;
    private final String greaterViolationMessagePrefix;

    RangeBigDecimalValidator(double fromDouble, double toDouble, Range.Boundary boundary) {
        if (toDouble < fromDouble) {
            throw new IllegalArgumentException("Invalid range bounds: to must be >= from, got from=" + fromDouble + ", to=" + toDouble);
        }

        this.from = BigDecimal.valueOf(fromDouble);
        this.to = BigDecimal.valueOf(toDouble);

        this.fromInclusive = boundary == Range.Boundary.INCLUSIVE_INCLUSIVE || boundary == Range.Boundary.INCLUSIVE_EXCLUSIVE;
        this.toInclusive = boundary == Range.Boundary.EXCLUSIVE_INCLUSIVE || boundary == Range.Boundary.INCLUSIVE_INCLUSIVE;

        var baseMessage = "Should be in range from '" + this.from + "' to '" + this.to + "'";
        this.nullViolationMessage = baseMessage + ", but was null";
        this.smallerViolationMessagePrefix = baseMessage + ", but was smaller: ";
        this.greaterViolationMessagePrefix = baseMessage + ", but was greater: ";
    }

    @Override
    public List<Violation> validate(BigDecimal value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(nullViolationMessage));
        }

        var compareFrom = value.compareTo(from);
        var isFromValid = fromInclusive ? compareFrom >= 0 : compareFrom > 0;
        if (!isFromValid) {
            return List.of(context.violates(smallerViolationMessagePrefix + value));
        }

        var compareTo = value.compareTo(to);
        var isToValid = toInclusive ? compareTo <= 0 : compareTo < 0;
        if (!isToValid) {
            return List.of(context.violates(greaterViolationMessagePrefix + value));
        }

        return Collections.emptyList();
    }
}

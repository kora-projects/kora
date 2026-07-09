package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

final class NumberValidator<T extends Number> implements Validator<T> {

    enum Rule {
        MIN,
        MAX,
        POSITIVE,
        POSITIVE_OR_ZERO,
        NEGATIVE,
        NEGATIVE_OR_ZERO
    }

    private final Rule rule;
    private final BigDecimal boundary;

    NumberValidator(Rule rule, long boundary) {
        this.rule = rule;
        this.boundary = BigDecimal.valueOf(boundary);
    }

    NumberValidator(Rule rule) {
        this(rule, 0);
    }

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(messagePrefix() + ", but was null"));
        }

        final BigDecimal decimal;
        try {
            decimal = toBigDecimal(value);
        } catch (NumberFormatException e) {
            return List.of(context.violates(messagePrefix() + ", but was invalid number: " + value));
        }

        final int compare = decimal.compareTo(boundary);
        final boolean valid = switch (rule) {
            case MIN, POSITIVE_OR_ZERO -> compare >= 0;
            case MAX, NEGATIVE_OR_ZERO -> compare <= 0;
            case POSITIVE -> compare > 0;
            case NEGATIVE -> compare < 0;
        };

        if (!valid) {
            return List.of(context.violates(messagePrefix() + ", but was: " + value));
        }

        return Collections.emptyList();
    }

    private String messagePrefix() {
        return switch (rule) {
            case MIN -> "Should be greater than or equal to '" + boundary.toPlainString() + "'";
            case MAX -> "Should be less than or equal to '" + boundary.toPlainString() + "'";
            case POSITIVE -> "Should be positive";
            case POSITIVE_OR_ZERO -> "Should be positive or zero";
            case NEGATIVE -> "Should be negative";
            case NEGATIVE_OR_ZERO -> "Should be negative or zero";
        };
    }

    private static BigDecimal toBigDecimal(Number value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof BigInteger bi) {
            return new BigDecimal(bi);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(value.longValue());
        }
        return new BigDecimal(value.toString());
    }
}

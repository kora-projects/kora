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

    // Кэшируем сообщения для полной ликвидации аллокаций строк в hot path
    private final String messagePrefix;
    private final String nullViolationMessage;
    private final String invalidNumberViolationMessagePrefix;
    private final String digitsViolationMessagePrefix;

    NumberValidator(Rule rule, long boundary) {
        this.rule = rule;
        this.boundary = BigDecimal.valueOf(boundary);

        if (rule == Rule.MIN) {
            this.messagePrefix = "Should be greater than or equal to '" + this.boundary.toPlainString() + "'";
        } else if (rule == Rule.MAX) {
            this.messagePrefix = "Should be less than or equal to '" + this.boundary.toPlainString() + "'";
        } else if (rule == Rule.POSITIVE) {
            this.messagePrefix = "Should be positive";
        } else if (rule == Rule.POSITIVE_OR_ZERO) {
            this.messagePrefix = "Should be positive or zero";
        } else if (rule == Rule.NEGATIVE) {
            this.messagePrefix = "Should be negative";
        } else if (rule == Rule.NEGATIVE_OR_ZERO) {
            this.messagePrefix = "Should be negative or zero";
        } else {
            this.messagePrefix = "";
        }

        this.nullViolationMessage = this.messagePrefix + ", but was null";
        this.invalidNumberViolationMessagePrefix = this.messagePrefix + ", but was invalid number: ";
        this.digitsViolationMessagePrefix = this.messagePrefix + ", but was: ";
    }

    NumberValidator(Rule rule) {
        this(rule, 0);
    }

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(nullViolationMessage));
        }

        final BigDecimal decimal;
        try {
            decimal = toBigDecimal(value);
        } catch (NumberFormatException e) {
            return List.of(context.violates(invalidNumberViolationMessagePrefix + value));
        }

        var compare = decimal.compareTo(boundary);
        boolean valid = false;

        if (rule == Rule.MIN || rule == Rule.POSITIVE_OR_ZERO) {
            valid = compare >= 0;
        } else if (rule == Rule.MAX || rule == Rule.NEGATIVE_OR_ZERO) {
            valid = compare <= 0;
        } else if (rule == Rule.POSITIVE) {
            valid = compare > 0;
        } else if (rule == Rule.NEGATIVE) {
            valid = compare < 0;
        }

        if (!valid) {
            return List.of(context.violates(digitsViolationMessagePrefix + value));
        }

        return Collections.emptyList();
    }

    private static BigDecimal toBigDecimal(Number value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof BigInteger bi) {
            return new BigDecimal(bi);
        }
        if (value instanceof Long l) {
            return BigDecimal.valueOf(l);
        }
        if (value instanceof Integer i) {
            return BigDecimal.valueOf(i);
        }
        if (value instanceof Short s) {
            return BigDecimal.valueOf(s);
        }
        if (value instanceof Byte b) {
            return BigDecimal.valueOf(b);
        }
        return new BigDecimal(value.toString());
    }
}

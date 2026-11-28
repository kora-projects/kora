package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

final class DigitsValidator<T> implements Validator<T> {

    private final int integer;
    private final int fraction;
    private final String nullViolationMessage;
    private final String invalidNumberViolationMessagePrefix;
    private final String digitsViolationMessage;

    DigitsValidator(int integer, int fraction) {
        if (integer < 0) {
            throw new IllegalArgumentException("Invalid digits integer value: integer must be >= 0, got " + integer);
        }
        if (fraction < 0) {
            throw new IllegalArgumentException("Invalid digits fraction value: fraction must be >= 0, got " + fraction);
        }

        this.integer = integer;
        this.fraction = fraction;

        var baseMessage = "Should have digits with integer part up to '" + integer + "' and fraction part up to '" + fraction + "'";
        this.nullViolationMessage = baseMessage + ", but was null";
        this.invalidNumberViolationMessagePrefix = baseMessage + ", but was invalid number: ";
        this.digitsViolationMessage = baseMessage + ", but was: ";
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

        var normalized = decimal.stripTrailingZeros();
        var precision = normalized.precision();
        var scale = Math.max(normalized.scale(), 0);
        var integerDigits = Math.max(precision - scale, 0);
        if (integerDigits > integer || scale > fraction) {
            return List.of(context.violates(digitsViolationMessage + value));
        }

        return Collections.emptyList();
    }

    private static BigDecimal toBigDecimal(Object value) {
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

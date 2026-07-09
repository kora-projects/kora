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

    DigitsValidator(int integer, int fraction) {
        if (integer < 0) {
            throw new IllegalArgumentException("Integer can't be less 0, but was: " + integer);
        }
        if (fraction < 0) {
            throw new IllegalArgumentException("Fraction can't be less 0, but was: " + fraction);
        }

        this.integer = integer;
        this.fraction = fraction;
    }

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Should have digits with integer part up to '" + integer + "' and fraction part up to '" + fraction + "', but was null"));
        }

        final BigDecimal decimal;
        try {
            decimal = toBigDecimal(value);
        } catch (NumberFormatException e) {
            return List.of(context.violates("Should have digits with integer part up to '" + integer + "' and fraction part up to '" + fraction + "', but was invalid number: " + value));
        }

        var normalized = decimal.stripTrailingZeros();
        var precision = normalized.precision();
        var scale = Math.max(normalized.scale(), 0);
        var integerDigits = Math.max(precision - scale, 0);
        if (integerDigits > integer || scale > fraction) {
            return List.of(context.violates("Should have digits with integer part up to '" + integer + "' and fraction part up to '" + fraction + "', but was: " + value));
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
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(((Number) value).longValue());
        }
        return new BigDecimal(value.toString());
    }
}

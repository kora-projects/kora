package ru.tinkoff.kora.validation.common.constraint;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.annotation.Range;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

class ValidationTests extends Assertions implements ValidatorModule {

    private static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of(new NotEmptyMapValidator<>(), Map.of(), 1),
            Arguments.of(new NotEmptyMapValidator<>(), Map.of(1, 1), 0),
            Arguments.of(new NotEmptyIterableValidator<>(), List.of(), 1),
            Arguments.of(new NotEmptyIterableValidator<>(), List.of(1), 0),
            Arguments.of(new NotBlankStringValidator<>(), "  ", 1),
            Arguments.of(new NotBlankStringValidator<>(), "", 1),
            Arguments.of(new NotBlankStringValidator<>(), "a", 0),
            Arguments.of(new NotEmptyStringValidator<>(), "", 1),
            Arguments.of(new NotEmptyStringValidator<>(), "a", 0),
            Arguments.of(new PatternValidator<>("\\d", 0), "a", 1),
            Arguments.of(new PatternValidator<>("\\d", 0), "1", 0),
            Arguments.of(new PatternValidator<>("[A-Z]+", 0), "AZ", 0),
            Arguments.of(new PatternValidator<>("[A-Z]+", 0), "A1Z", 1),
            Arguments.of(new RangeBigDecimalValidator(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), BigDecimal.valueOf(2.5), 1),
            Arguments.of(new RangeBigDecimalValidator(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), BigDecimal.valueOf(0.5), 1),
            Arguments.of(new RangeBigDecimalValidator(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), BigDecimal.valueOf(2), 1),
            Arguments.of(new RangeBigDecimalValidator(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), BigDecimal.valueOf(1), 1),
            Arguments.of(new RangeBigDecimalValidator(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), BigDecimal.valueOf(1.9), 0),
            Arguments.of(new RangeBigDecimalValidator(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), BigDecimal.valueOf(1.1), 0),
            Arguments.of(new RangeBigDecimalValidator(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), BigDecimal.valueOf(2.5), 1),
            Arguments.of(new RangeBigDecimalValidator(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), BigDecimal.valueOf(0.5), 1),
            Arguments.of(new RangeBigDecimalValidator(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), BigDecimal.valueOf(2), 0),
            Arguments.of(new RangeBigDecimalValidator(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), BigDecimal.valueOf(1), 0),
            Arguments.of(new RangeBigDecimalValidator(1, Long.MAX_VALUE, Range.Boundary.INCLUSIVE_INCLUSIVE), BigDecimal.valueOf(10), 0),
            Arguments.of(new RangeBigDecimalValidator(1, Long.MAX_VALUE, Range.Boundary.INCLUSIVE_INCLUSIVE), BigDecimal.valueOf(-1), 1),
            Arguments.of(new RangeBigIntegerValidator(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), BigInteger.valueOf(3), 1),
            Arguments.of(new RangeBigIntegerValidator(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), BigInteger.valueOf(0), 1),
            Arguments.of(new RangeBigIntegerValidator(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), BigInteger.valueOf(2), 1),
            Arguments.of(new RangeBigIntegerValidator(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), BigInteger.valueOf(1), 1),
            Arguments.of(new RangeBigIntegerValidator(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), BigInteger.valueOf(2), 1),
            Arguments.of(new RangeBigIntegerValidator(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), BigInteger.valueOf(1), 1),
            Arguments.of(new RangeBigIntegerValidator(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), BigInteger.valueOf(3), 1),
            Arguments.of(new RangeBigIntegerValidator(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), BigInteger.valueOf(0), 1),
            Arguments.of(new RangeBigIntegerValidator(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), BigInteger.valueOf(2), 0),
            Arguments.of(new RangeBigIntegerValidator(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), BigInteger.valueOf(1), 0),
            Arguments.of(new RangeBigIntegerValidator(1, Long.MAX_VALUE, Range.Boundary.INCLUSIVE_INCLUSIVE), BigInteger.valueOf(10), 0),
            Arguments.of(new RangeBigIntegerValidator(1, Long.MAX_VALUE, Range.Boundary.INCLUSIVE_INCLUSIVE), BigInteger.valueOf(-1), 1),
            Arguments.of(new RangeLongNumberValidator<>(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), 3, 1),
            Arguments.of(new RangeLongNumberValidator<>(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), 0, 1),
            Arguments.of(new RangeLongNumberValidator<>(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), 2, 1),
            Arguments.of(new RangeLongNumberValidator<>(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), 1, 1),
            Arguments.of(new RangeLongNumberValidator<>(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), 2, 1),
            Arguments.of(new RangeLongNumberValidator<>(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), 1, 1),
            Arguments.of(new RangeLongNumberValidator<>(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), 3, 1),
            Arguments.of(new RangeLongNumberValidator<>(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), 0, 1),
            Arguments.of(new RangeLongNumberValidator<>(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), 2, 0),
            Arguments.of(new RangeLongNumberValidator<>(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), 1, 0),
            Arguments.of(new RangeLongNumberValidator<>(1, Long.MAX_VALUE, Range.Boundary.INCLUSIVE_INCLUSIVE), 10, 0),
            Arguments.of(new RangeLongNumberValidator<>(1, Long.MAX_VALUE, Range.Boundary.INCLUSIVE_INCLUSIVE), -1, 1),
            Arguments.of(new RangeDoubleNumberValidator<>(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), 2.5, 1),
            Arguments.of(new RangeDoubleNumberValidator<>(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), 0.5, 1),
            Arguments.of(new RangeDoubleNumberValidator<>(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), 2, 1),
            Arguments.of(new RangeDoubleNumberValidator<>(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), 1, 1),
            Arguments.of(new RangeDoubleNumberValidator<>(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), 1.9, 0),
            Arguments.of(new RangeDoubleNumberValidator<>(1, 2, Range.Boundary.EXCLUSIVE_EXCLUSIVE), 1.1, 0),
            Arguments.of(new RangeDoubleNumberValidator<>(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), 2.5, 1),
            Arguments.of(new RangeDoubleNumberValidator<>(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), 0.5, 1),
            Arguments.of(new RangeDoubleNumberValidator<>(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), 2, 0),
            Arguments.of(new RangeDoubleNumberValidator<>(1, 2, Range.Boundary.INCLUSIVE_INCLUSIVE), 1, 0),
            Arguments.of(new RangeDoubleNumberValidator<>(1, Long.MAX_VALUE, Range.Boundary.INCLUSIVE_INCLUSIVE), 10, 0),
            Arguments.of(new RangeDoubleNumberValidator<>(1, Long.MAX_VALUE, Range.Boundary.INCLUSIVE_INCLUSIVE), -1, 1),
            Arguments.of(new SizeMapValidator<>(2, 3), Map.of(1, 1, 2, 2), 0),
            Arguments.of(new SizeMapValidator<>(2, 3), Map.of(1, 1, 2, 2, 3, 3, 4, 4), 1),
            Arguments.of(new SizeMapValidator<>(2, 3), Map.of(1, 1), 1),
            Arguments.of(new SizeCollectionValidator<>(2, 3), List.of(1, 2), 0),
            Arguments.of(new SizeCollectionValidator<>(2, 3), List.of(1, 2, 3, 4), 1),
            Arguments.of(new SizeCollectionValidator<>(2, 3), List.of(1), 1),
            Arguments.of(new SizeStringValidator<String>(2, 3), "12", 0),
            Arguments.of(new SizeStringValidator<String>(2, 3), "1234", 1),
            Arguments.of(new SizeStringValidator<String>(2, 3), "1", 1)
        );
    }

    @MethodSource("source")
    @ParameterizedTest
    void checkViolations(Validator validator, Object value, int expectedViolations) {
        var violations = validator.validate(value);
        assertEquals(expectedViolations, violations.size());
    }

    @Test
    void boundaryFailureForDouble() {
        assertThrows(IllegalArgumentException.class, () -> new RangeDoubleNumberValidator<>(2.0, 1.0, Range.Boundary.INCLUSIVE_INCLUSIVE));
    }

    @Test
    void boundaryFailureForBigDecimal() {
        assertThrows(IllegalArgumentException.class, () -> new RangeBigDecimalValidator(2.0, 1.0, Range.Boundary.INCLUSIVE_INCLUSIVE));
    }

    @Test
    void boundaryFailureForBigInteger() {
        assertThrows(IllegalArgumentException.class, () -> new RangeBigIntegerValidator(2.0, 1.0, Range.Boundary.INCLUSIVE_INCLUSIVE));
    }

    @Test
    void boundaryFailureForLong() {
        assertThrows(IllegalArgumentException.class, () -> new RangeLongNumberValidator<>(2.0, 1.0, Range.Boundary.INCLUSIVE_INCLUSIVE));
    }
}

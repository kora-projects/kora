package io.koraframework.validation.common.constraint;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.annotation.Range;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
            Arguments.of(new SizeStringValidator<String>(2, 3), "1", 1),
            Arguments.of(new NumberValidator<>(NumberValidator.Rule.MIN, 10), 10, 0),
            Arguments.of(new NumberValidator<>(NumberValidator.Rule.MIN, 10), 9, 1),
            Arguments.of(new NumberValidator<>(NumberValidator.Rule.MAX, 10), 10, 0),
            Arguments.of(new NumberValidator<>(NumberValidator.Rule.MAX, 10), 11, 1),
            Arguments.of(new NumberValidator<>(NumberValidator.Rule.POSITIVE), BigDecimal.valueOf(1), 0),
            Arguments.of(new NumberValidator<>(NumberValidator.Rule.POSITIVE), BigDecimal.ZERO, 1),
            Arguments.of(new NumberValidator<>(NumberValidator.Rule.POSITIVE_OR_ZERO), BigInteger.ZERO, 0),
            Arguments.of(new NumberValidator<>(NumberValidator.Rule.NEGATIVE), -1, 0),
            Arguments.of(new NumberValidator<>(NumberValidator.Rule.NEGATIVE), 0, 1),
            Arguments.of(new NumberValidator<>(NumberValidator.Rule.NEGATIVE_OR_ZERO), 0, 0),
            Arguments.of(new UrlValidator<>(), "https://kora-projects.github.io/kora-docs/", 0),
            Arguments.of(new UrlValidator<>(), "/kora-docs/", 1),
            Arguments.of(new UriValidator<>(), "/kora-docs/", 0),
            Arguments.of(new UriValidator<>(), "http://[invalid", 1),
            Arguments.of(new UuidValidator<>(), "123e4567-e89b-12d3-a456-426614174000", 0),
            Arguments.of(new UuidValidator<>(), "not-uuid", 1),
            Arguments.of(new OneOfValidator<>(new String[]{"NEW", "DONE"}), "NEW", 0),
            Arguments.of(new OneOfValidator<>(new String[]{"NEW", "DONE"}), "FAIL", 1),
            Arguments.of(new DigitsValidator<>(3, 2), BigDecimal.valueOf(123.45), 0),
            Arguments.of(new DigitsValidator<>(3, 2), BigDecimal.valueOf(1234.5), 1),
            Arguments.of(new DigitsValidator<>(3, 2), "12.345", 1),
            Arguments.of(new BooleanValidator(true), true, 0),
            Arguments.of(new BooleanValidator(true), false, 1),
            Arguments.of(new BooleanValidator(false), false, 0),
            Arguments.of(new BooleanValidator(false), true, 1),
            Arguments.of(new TemporalValidator<>(TemporalValidator.Rule.PAST, LocalDate::now), LocalDate.now().minusDays(1), 0),
            Arguments.of(new TemporalValidator<>(TemporalValidator.Rule.PAST, LocalDate::now), LocalDate.now().plusDays(1), 1),
            Arguments.of(new TemporalValidator<>(TemporalValidator.Rule.PAST_OR_PRESENT, LocalDate::now), LocalDate.now(), 0),
            Arguments.of(new TemporalValidator<>(TemporalValidator.Rule.FUTURE, Instant::now), Instant.now().plusSeconds(60), 0),
            Arguments.of(new TemporalValidator<>(TemporalValidator.Rule.FUTURE, Instant::now), Instant.now().minusSeconds(60), 1),
            Arguments.of(new TemporalValidator<>(TemporalValidator.Rule.FUTURE_OR_PRESENT, LocalDateTime::now), LocalDateTime.now().plusSeconds(1), 0)
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

    @Test
    void digitsFailureForNegativeBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new DigitsValidator<>(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new DigitsValidator<>(1, -1));
    }
}

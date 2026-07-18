package io.koraframework.validation.common.constraint;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ValidatorModule {

    @DefaultComponent
    default <T> Validator<List<T>> listValidator(Validator<T> validator, TypeRef<T> valueRef) {
        return new IterableValidator<>(validator);
    }

    @DefaultComponent
    default <T> Validator<Set<T>> setValidator(Validator<T> validator, TypeRef<T> valueRef) {
        return new IterableValidator<>(validator);
    }

    @DefaultComponent
    default <T> Validator<Collection<T>> collectionValidator(Validator<T> validator, TypeRef<T> valueRef) {
        return new IterableValidator<>(validator);
    }

    @DefaultComponent
    default <K, V> NotEmptyValidatorFactory<Map<K, V>> notEmptyMapValidatorFactory(TypeRef<K> keyRef, TypeRef<V> valueRef) {
        return NotEmptyMapValidator::new;
    }

    @DefaultComponent
    default <T> NotEmptyValidatorFactory<Iterable<T>> notEmptyIterableValidatorFactory(TypeRef<T> valueRef) {
        return NotEmptyIterableValidator::new;
    }

    @DefaultComponent
    default <T> NotEmptyValidatorFactory<List<T>> notEmptyListValidatorFactory(TypeRef<T> valueRef) {
        return NotEmptyIterableValidator::new;
    }

    @DefaultComponent
    default <T> NotEmptyValidatorFactory<Set<T>> notEmptySetValidatorFactory(TypeRef<T> valueRef) {
        return NotEmptyIterableValidator::new;
    }

    @DefaultComponent
    default <T> NotEmptyValidatorFactory<Collection<T>> notEmptyCollectionValidatorFactory(TypeRef<T> valueRef) {
        return NotEmptyIterableValidator::new;
    }

    @DefaultComponent
    default NotEmptyValidatorFactory<String> notEmptyStringValidatorFactory() {
        return NotEmptyStringValidator::new;
    }

    @DefaultComponent
    default NotEmptyValidatorFactory<CharSequence> notEmptyCharSequenceValidatorFactory() {
        return NotEmptyStringValidator::new;
    }

    @DefaultComponent
    default NotBlankValidatorFactory<String> notBlankStringValidatorFactory() {
        return NotBlankStringValidator::new;
    }

    @DefaultComponent
    default NotBlankValidatorFactory<CharSequence> notBlankCharSequenceValidatorFactory() {
        return NotBlankStringValidator::new;
    }

    @DefaultComponent
    default RangeValidatorFactory<Short> rangeShortValidatorFactory() {
        return RangeLongNumberValidator::new;
    }

    @DefaultComponent
    default RangeValidatorFactory<Integer> rangeIntegerValidatorFactory() {
        return RangeLongNumberValidator::new;
    }

    @DefaultComponent
    default RangeValidatorFactory<Long> rangeLongValidatorFactory() {
        return RangeLongNumberValidator::new;
    }

    @DefaultComponent
    default RangeValidatorFactory<Float> rangeFloatValidatorFactory() {
        return RangeDoubleNumberValidator::new;
    }

    @DefaultComponent
    default RangeValidatorFactory<Double> rangeDoubleValidatorFactory() {
        return RangeDoubleNumberValidator::new;
    }

    @DefaultComponent
    default RangeValidatorFactory<BigInteger> rangeBigIntegerValidatorFactory() {
        return RangeBigIntegerValidator::new;
    }

    @DefaultComponent
    default RangeValidatorFactory<BigDecimal> rangeBigDecimalValidatorFactory() {
        return RangeBigDecimalValidator::new;
    }

    @DefaultComponent
    default SizeValidatorFactory<String> sizeStringValidatorFactory() {
        return SizeStringValidator::new;
    }

    @DefaultComponent
    default SizeValidatorFactory<CharSequence> sizeCharSequenceValidatorFactory() {
        return SizeStringValidator::new;
    }

    @DefaultComponent
    default <K, V> SizeValidatorFactory<Map<K, V>> sizeDoubleValidatorFactory(TypeRef<K> keyRef, TypeRef<V> valueRef) {
        return SizeMapValidator::new;
    }

    @DefaultComponent
    default <V> SizeValidatorFactory<Collection<V>> sizeIterableValidatorFactory(TypeRef<V> valueRef) {
        return SizeCollectionValidator::new;
    }

    @DefaultComponent
    default <V> SizeValidatorFactory<List<V>> sizeListValidatorFactory(TypeRef<V> valueRef) {
        return SizeCollectionValidator::new;
    }

    @DefaultComponent
    default <V> SizeValidatorFactory<Set<V>> sizeSetValidatorFactory(TypeRef<V> valueRef) {
        return SizeCollectionValidator::new;
    }

    @DefaultComponent
    default PatternValidatorFactory<String> patternStringValidatorFactory() {
        return PatternValidator::new;
    }

    @DefaultComponent
    default PatternValidatorFactory<CharSequence> patternCharSequenceValidatorFactory() {
        return PatternValidator::new;
    }

    @DefaultComponent
    default MinValidatorFactory<Short> minShortValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MIN, value);
    }

    @DefaultComponent
    default MinValidatorFactory<Integer> minIntegerValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MIN, value);
    }

    @DefaultComponent
    default MinValidatorFactory<Long> minLongValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MIN, value);
    }

    @DefaultComponent
    default MinValidatorFactory<Float> minFloatValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MIN, value);
    }

    @DefaultComponent
    default MinValidatorFactory<Double> minDoubleValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MIN, value);
    }

    @DefaultComponent
    default MinValidatorFactory<BigInteger> minBigIntegerValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MIN, value);
    }

    @DefaultComponent
    default MinValidatorFactory<BigDecimal> minBigDecimalValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MIN, value);
    }

    @DefaultComponent
    default MaxValidatorFactory<Short> maxShortValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MAX, value);
    }

    @DefaultComponent
    default MaxValidatorFactory<Integer> maxIntegerValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MAX, value);
    }

    @DefaultComponent
    default MaxValidatorFactory<Long> maxLongValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MAX, value);
    }

    @DefaultComponent
    default MaxValidatorFactory<Float> maxFloatValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MAX, value);
    }

    @DefaultComponent
    default MaxValidatorFactory<Double> maxDoubleValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MAX, value);
    }

    @DefaultComponent
    default MaxValidatorFactory<BigInteger> maxBigIntegerValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MAX, value);
    }

    @DefaultComponent
    default MaxValidatorFactory<BigDecimal> maxBigDecimalValidatorFactory() {
        return value -> new NumberValidator<>(NumberValidator.Rule.MAX, value);
    }

    @DefaultComponent
    default <T extends Number> PositiveValidatorFactory<T> positiveValidatorFactory(TypeRef<T> valueRef) {
        return () -> new NumberValidator<>(NumberValidator.Rule.POSITIVE);
    }

    @DefaultComponent
    default <T extends Number> PositiveOrZeroValidatorFactory<T> positiveOrZeroValidatorFactory(TypeRef<T> valueRef) {
        return () -> new NumberValidator<>(NumberValidator.Rule.POSITIVE_OR_ZERO);
    }

    @DefaultComponent
    default <T extends Number> NegativeValidatorFactory<T> negativeValidatorFactory(TypeRef<T> valueRef) {
        return () -> new NumberValidator<>(NumberValidator.Rule.NEGATIVE);
    }

    @DefaultComponent
    default <T extends Number> NegativeOrZeroValidatorFactory<T> negativeOrZeroValidatorFactory(TypeRef<T> valueRef) {
        return () -> new NumberValidator<>(NumberValidator.Rule.NEGATIVE_OR_ZERO);
    }

    @DefaultComponent
    default UrlValidatorFactory<String> urlStringValidatorFactory() {
        return UrlValidator::new;
    }

    @DefaultComponent
    default UrlValidatorFactory<CharSequence> urlCharSequenceValidatorFactory() {
        return UrlValidator::new;
    }

    @DefaultComponent
    default UriValidatorFactory<String> uriStringValidatorFactory() {
        return UriValidator::new;
    }

    @DefaultComponent
    default UriValidatorFactory<CharSequence> uriCharSequenceValidatorFactory() {
        return UriValidator::new;
    }

    @DefaultComponent
    default DigitsValidatorFactory<Short> digitsShortValidatorFactory() {
        return DigitsValidator::new;
    }

    @DefaultComponent
    default DigitsValidatorFactory<Integer> digitsIntegerValidatorFactory() {
        return DigitsValidator::new;
    }

    @DefaultComponent
    default DigitsValidatorFactory<Long> digitsLongValidatorFactory() {
        return DigitsValidator::new;
    }

    @DefaultComponent
    default DigitsValidatorFactory<Float> digitsFloatValidatorFactory() {
        return DigitsValidator::new;
    }

    @DefaultComponent
    default DigitsValidatorFactory<Double> digitsDoubleValidatorFactory() {
        return DigitsValidator::new;
    }

    @DefaultComponent
    default DigitsValidatorFactory<BigInteger> digitsBigIntegerValidatorFactory() {
        return DigitsValidator::new;
    }

    @DefaultComponent
    default DigitsValidatorFactory<BigDecimal> digitsBigDecimalValidatorFactory() {
        return DigitsValidator::new;
    }

    @DefaultComponent
    default DigitsValidatorFactory<String> digitsStringValidatorFactory() {
        return DigitsValidator::new;
    }

    @DefaultComponent
    default DigitsValidatorFactory<CharSequence> digitsCharSequenceValidatorFactory() {
        return DigitsValidator::new;
    }

    @DefaultComponent
    default AssertTrueValidatorFactory<Boolean> assertTrueValidatorFactory() {
        return () -> new BooleanValidator(true);
    }

    @DefaultComponent
    default AssertFalseValidatorFactory<Boolean> assertFalseValidatorFactory() {
        return () -> new BooleanValidator(false);
    }

    @DefaultComponent
    default PastValidatorFactory<LocalDate> pastLocalDateValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.PAST, LocalDate::now);
    }

    @DefaultComponent
    default PastOrPresentValidatorFactory<LocalDate> pastOrPresentLocalDateValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.PAST_OR_PRESENT, LocalDate::now);
    }

    @DefaultComponent
    default FutureValidatorFactory<LocalDate> futureLocalDateValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.FUTURE, LocalDate::now);
    }

    @DefaultComponent
    default FutureOrPresentValidatorFactory<LocalDate> futureOrPresentLocalDateValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.FUTURE_OR_PRESENT, LocalDate::now);
    }

    @DefaultComponent
    default PastValidatorFactory<LocalDateTime> pastLocalDateTimeValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.PAST, LocalDateTime::now);
    }

    @DefaultComponent
    default PastOrPresentValidatorFactory<LocalDateTime> pastOrPresentLocalDateTimeValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.PAST_OR_PRESENT, LocalDateTime::now);
    }

    @DefaultComponent
    default FutureValidatorFactory<LocalDateTime> futureLocalDateTimeValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.FUTURE, LocalDateTime::now);
    }

    @DefaultComponent
    default FutureOrPresentValidatorFactory<LocalDateTime> futureOrPresentLocalDateTimeValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.FUTURE_OR_PRESENT, LocalDateTime::now);
    }

    @DefaultComponent
    default PastValidatorFactory<Instant> pastInstantValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.PAST, Instant::now);
    }

    @DefaultComponent
    default PastOrPresentValidatorFactory<Instant> pastOrPresentInstantValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.PAST_OR_PRESENT, Instant::now);
    }

    @DefaultComponent
    default FutureValidatorFactory<Instant> futureInstantValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.FUTURE, Instant::now);
    }

    @DefaultComponent
    default FutureOrPresentValidatorFactory<Instant> futureOrPresentInstantValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.FUTURE_OR_PRESENT, Instant::now);
    }

    @DefaultComponent
    default PastValidatorFactory<OffsetDateTime> pastOffsetDateTimeValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.PAST, OffsetDateTime::now);
    }

    @DefaultComponent
    default PastOrPresentValidatorFactory<OffsetDateTime> pastOrPresentOffsetDateTimeValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.PAST_OR_PRESENT, OffsetDateTime::now);
    }

    @DefaultComponent
    default FutureValidatorFactory<OffsetDateTime> futureOffsetDateTimeValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.FUTURE, OffsetDateTime::now);
    }

    @DefaultComponent
    default FutureOrPresentValidatorFactory<OffsetDateTime> futureOrPresentOffsetDateTimeValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.FUTURE_OR_PRESENT, OffsetDateTime::now);
    }

    @DefaultComponent
    default PastValidatorFactory<ZonedDateTime> pastZonedDateTimeValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.PAST, ZonedDateTime::now);
    }

    @DefaultComponent
    default PastOrPresentValidatorFactory<ZonedDateTime> pastOrPresentZonedDateTimeValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.PAST_OR_PRESENT, ZonedDateTime::now);
    }

    @DefaultComponent
    default FutureValidatorFactory<ZonedDateTime> futureZonedDateTimeValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.FUTURE, ZonedDateTime::now);
    }

    @DefaultComponent
    default FutureOrPresentValidatorFactory<ZonedDateTime> futureOrPresentZonedDateTimeValidatorFactory() {
        return () -> new TemporalValidator<>(TemporalValidator.Rule.FUTURE_OR_PRESENT, ZonedDateTime::now);
    }

    @DefaultComponent
    default UuidValidatorFactory<String> uuidStringValidatorFactory() {
        return UuidValidator::new;
    }

    @DefaultComponent
    default UuidValidatorFactory<CharSequence> uuidCharSequenceValidatorFactory() {
        return UuidValidator::new;
    }

    @DefaultComponent
    default OneOfValidatorFactory<String> oneOfStringValidatorFactory() {
        return OneOfValidator::new;
    }

    @DefaultComponent
    default OneOfValidatorFactory<CharSequence> oneOfCharSequenceValidatorFactory() {
        return OneOfValidator::new;
    }
}

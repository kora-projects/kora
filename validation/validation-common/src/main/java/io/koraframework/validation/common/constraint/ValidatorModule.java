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
    default <K, V> Validator<Map<K, V>> mapValidator(Validator<V> valueValidator, TypeRef<K> keyRef, TypeRef<V> valueRef) {
        return new MapValidator<>(null, valueValidator);
    }

    @DefaultComponent
    default <K, V> NotEmptyValidatorFactory<Map<K, V>> notEmptyMapValidatorFactory(TypeRef<K> keyRef, TypeRef<V> valueRef) {
        return NotEmptyMapValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default <T> NotEmptyValidatorFactory<Iterable<T>> notEmptyIterableValidatorFactory(TypeRef<T> valueRef) {
        return NotEmptyIterableValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default <T> NotEmptyValidatorFactory<List<T>> notEmptyListValidatorFactory(TypeRef<T> valueRef) {
        return NotEmptyIterableValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default <T> NotEmptyValidatorFactory<Set<T>> notEmptySetValidatorFactory(TypeRef<T> valueRef) {
        return NotEmptyIterableValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default <T> NotEmptyValidatorFactory<Collection<T>> notEmptyCollectionValidatorFactory(TypeRef<T> valueRef) {
        return NotEmptyIterableValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default NotEmptyValidatorFactory<String> notEmptyStringValidatorFactory() {
        return NotEmptyStringValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default NotEmptyValidatorFactory<CharSequence> notEmptyCharSequenceValidatorFactory() {
        return NotEmptyCharSequenceValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default NotBlankValidatorFactory<String> notBlankStringValidatorFactory() {
        return NotBlankStringValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default NotBlankValidatorFactory<CharSequence> notBlankCharSequenceValidatorFactory() {
        return NotBlankCharSequenceValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default RangeValidatorFactory<Short> rangeShortValidatorFactory() {
        return RangeLongNumberValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default RangeValidatorFactory<Integer> rangeIntegerValidatorFactory() {
        return RangeLongNumberValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default RangeValidatorFactory<Long> rangeLongValidatorFactory() {
        return RangeLongNumberValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default RangeValidatorFactory<Float> rangeFloatValidatorFactory() {
        return RangeDoubleNumberValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default RangeValidatorFactory<Double> rangeDoubleValidatorFactory() {
        return RangeDoubleNumberValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default RangeValidatorFactory<BigInteger> rangeBigIntegerValidatorFactory() {
        return RangeBigIntegerValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default RangeValidatorFactory<BigDecimal> rangeBigDecimalValidatorFactory() {
        return RangeBigDecimalValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default SizeValidatorFactory<String> sizeStringValidatorFactory() {
        return SizeStringValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default SizeValidatorFactory<CharSequence> sizeCharSequenceValidatorFactory() {
        return SizeStringValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default <K, V> SizeValidatorFactory<Map<K, V>> sizeDoubleValidatorFactory(TypeRef<K> keyRef, TypeRef<V> valueRef) {
        return SizeMapValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default <V> SizeValidatorFactory<Collection<V>> sizeIterableValidatorFactory(TypeRef<V> valueRef) {
        return SizeCollectionValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default <V> SizeValidatorFactory<List<V>> sizeListValidatorFactory(TypeRef<V> valueRef) {
        return SizeCollectionValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default <V> SizeValidatorFactory<Set<V>> sizeSetValidatorFactory(TypeRef<V> valueRef) {
        return SizeCollectionValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default PatternValidatorFactory<String> patternStringValidatorFactory() {
        return PatternValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default PatternValidatorFactory<CharSequence> patternCharSequenceValidatorFactory() {
        return PatternValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MinValidatorFactory<Short> minShortValidatorFactory() {
        return MinValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MinValidatorFactory<Integer> minIntegerValidatorFactory() {
        return MinValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MinValidatorFactory<Long> minLongValidatorFactory() {
        return MinValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MinValidatorFactory<Float> minFloatValidatorFactory() {
        return MinValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MinValidatorFactory<Double> minDoubleValidatorFactory() {
        return MinValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MinValidatorFactory<BigInteger> minBigIntegerValidatorFactory() {
        return MinValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MinValidatorFactory<BigDecimal> minBigDecimalValidatorFactory() {
        return MinValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MaxValidatorFactory<Short> maxShortValidatorFactory() {
        return MaxValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MaxValidatorFactory<Integer> maxIntegerValidatorFactory() {
        return MaxValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MaxValidatorFactory<Long> maxLongValidatorFactory() {
        return MaxValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MaxValidatorFactory<Float> maxFloatValidatorFactory() {
        return MaxValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MaxValidatorFactory<Double> maxDoubleValidatorFactory() {
        return MaxValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MaxValidatorFactory<BigInteger> maxBigIntegerValidatorFactory() {
        return MaxValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default MaxValidatorFactory<BigDecimal> maxBigDecimalValidatorFactory() {
        return MaxValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default <T extends Number> PositiveValidatorFactory<T> positiveValidatorFactory(TypeRef<T> valueRef) {
        return PositiveValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default <T extends Number> PositiveOrZeroValidatorFactory<T> positiveOrZeroValidatorFactory(TypeRef<T> valueRef) {
        return PositiveOrZeroValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default <T extends Number> NegativeValidatorFactory<T> negativeValidatorFactory(TypeRef<T> valueRef) {
        return NegativeValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default <T extends Number> NegativeOrZeroValidatorFactory<T> negativeOrZeroValidatorFactory(TypeRef<T> valueRef) {
        return NegativeOrZeroValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default UrlValidatorFactory<String> urlStringValidatorFactory() {
        return UrlValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default UrlValidatorFactory<CharSequence> urlCharSequenceValidatorFactory() {
        return UrlValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default UriValidatorFactory<String> uriStringValidatorFactory() {
        return UriValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default UriValidatorFactory<CharSequence> uriCharSequenceValidatorFactory() {
        return UriValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default DigitsValidatorFactory<Short> digitsShortValidatorFactory() {
        return DigitsValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default DigitsValidatorFactory<Integer> digitsIntegerValidatorFactory() {
        return DigitsValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default DigitsValidatorFactory<Long> digitsLongValidatorFactory() {
        return DigitsValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default DigitsValidatorFactory<Float> digitsFloatValidatorFactory() {
        return DigitsValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default DigitsValidatorFactory<Double> digitsDoubleValidatorFactory() {
        return DigitsValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default DigitsValidatorFactory<BigInteger> digitsBigIntegerValidatorFactory() {
        return DigitsValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default DigitsValidatorFactory<BigDecimal> digitsBigDecimalValidatorFactory() {
        return DigitsValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default DigitsValidatorFactory<String> digitsStringValidatorFactory() {
        return DigitsValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default DigitsValidatorFactory<CharSequence> digitsCharSequenceValidatorFactory() {
        return DigitsValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default AssertTrueValidatorFactory<Boolean> assertTrueValidatorFactory() {
        return AssertTrueValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default AssertFalseValidatorFactory<Boolean> assertFalseValidatorFactory() {
        return AssertFalseValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default PastValidatorFactory<LocalDate> pastLocalDateValidatorFactory() {
        return PastLocalDateValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default PastOrPresentValidatorFactory<LocalDate> pastOrPresentLocalDateValidatorFactory() {
        return PastOrPresentLocalDateValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default FutureValidatorFactory<LocalDate> futureLocalDateValidatorFactory() {
        return FutureLocalDateValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default FutureOrPresentValidatorFactory<LocalDate> futureOrPresentLocalDateValidatorFactory() {
        return FutureOrPresentLocalDateValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default PastValidatorFactory<LocalDateTime> pastLocalDateTimeValidatorFactory() {
        return PastLocalDateTimeValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default PastOrPresentValidatorFactory<LocalDateTime> pastOrPresentLocalDateTimeValidatorFactory() {
        return PastOrPresentLocalDateTimeValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default FutureValidatorFactory<LocalDateTime> futureLocalDateTimeValidatorFactory() {
        return FutureLocalDateTimeValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default FutureOrPresentValidatorFactory<LocalDateTime> futureOrPresentLocalDateTimeValidatorFactory() {
        return FutureOrPresentLocalDateTimeValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default PastValidatorFactory<Instant> pastInstantValidatorFactory() {
        return PastInstantValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default PastOrPresentValidatorFactory<Instant> pastOrPresentInstantValidatorFactory() {
        return PastOrPresentInstantValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default FutureValidatorFactory<Instant> futureInstantValidatorFactory() {
        return FutureInstantValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default FutureOrPresentValidatorFactory<Instant> futureOrPresentInstantValidatorFactory() {
        return FutureOrPresentInstantValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default PastValidatorFactory<OffsetDateTime> pastOffsetDateTimeValidatorFactory() {
        return PastOffsetDateTimeValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default PastOrPresentValidatorFactory<OffsetDateTime> pastOrPresentOffsetDateTimeValidatorFactory() {
        return PastOrPresentOffsetDateTimeValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default FutureValidatorFactory<OffsetDateTime> futureOffsetDateTimeValidatorFactory() {
        return FutureOffsetDateTimeValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default FutureOrPresentValidatorFactory<OffsetDateTime> futureOrPresentOffsetDateTimeValidatorFactory() {
        return FutureOrPresentOffsetDateTimeValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default PastValidatorFactory<ZonedDateTime> pastZonedDateTimeValidatorFactory() {
        return PastZonedDateTimeValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default PastOrPresentValidatorFactory<ZonedDateTime> pastOrPresentZonedDateTimeValidatorFactory() {
        return PastOrPresentZonedDateTimeValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default FutureValidatorFactory<ZonedDateTime> futureZonedDateTimeValidatorFactory() {
        return FutureZonedDateTimeValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default FutureOrPresentValidatorFactory<ZonedDateTime> futureOrPresentZonedDateTimeValidatorFactory() {
        return FutureOrPresentZonedDateTimeValidatorFactoryImpl.INSTANCE;
    }

    @DefaultComponent
    default UuidValidatorFactory<String> uuidStringValidatorFactory() {
        return UuidValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default UuidValidatorFactory<CharSequence> uuidCharSequenceValidatorFactory() {
        return UuidValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default OneOfValidatorFactory<String> oneOfStringValidatorFactory() {
        return OneOfValidatorFactoryImpl.instance();
    }

    @DefaultComponent
    default OneOfValidatorFactory<CharSequence> oneOfCharSequenceValidatorFactory() {
        return OneOfValidatorFactoryImpl.instance();
    }
}

package io.koraframework.validation.common.constraint;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.*;

import java.math.BigDecimal;
import java.math.BigInteger;
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
}

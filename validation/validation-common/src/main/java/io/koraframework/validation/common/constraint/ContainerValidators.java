package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;

import java.util.Map;

/**
 * <b>Русский</b>: Валидаторы контейнеров, которые применяют валидатор к каждому элементу коллекции, ключу или значению {@link Map}
 * <hr>
 * <b>English</b>: Container validators that apply a validator to every element of a collection, every key or every value of a {@link Map}
 */
public final class ContainerValidators {

    private ContainerValidators() {}

    public static <T, I extends Iterable<T>> Validator<I> iterable(Validator<T> elementValidator) {
        return new IterableValidator<>(elementValidator);
    }

    public static <K, V, M extends Map<K, V>> Validator<M> mapKeys(Validator<K> keyValidator) {
        return new MapValidator<>(keyValidator, null);
    }

    public static <K, V, M extends Map<K, V>> Validator<M> mapValues(Validator<V> valueValidator) {
        return new MapValidator<>(null, valueValidator);
    }
}

package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <b>Русский</b>: Валидаторы контейнеров, которые за один проход применяют валидаторы к каждому элементу коллекции, ключу и значению {@link Map}
 * <hr>
 * <b>English</b>: Container validators that apply validators to every element of a collection, every key and every value of a {@link Map} in a single pass
 */
public final class ContainerValidators {

    private ContainerValidators() {}

    public static <T, I extends Iterable<? extends T>> Validator<I> iterable(Validator<? super T> elementValidator) {
        return new IterableValidator<>(elementValidator);
    }

    /**
     * @param keyValidator   validator of every key, {@code null} when keys are not validated
     * @param valueValidator validator of every value, {@code null} when values are not validated
     */
    public static <K, V, M extends Map<? extends K, ? extends V>> Validator<M> map(@Nullable Validator<? super K> keyValidator, @Nullable Validator<? super V> valueValidator) {
        return new MapValidator<>(keyValidator, valueValidator);
    }

    public static List<Violation> addViolation(@Nullable List<Violation> list, Violation violation) {
        if (list == null) list = new ArrayList<>();
        list.add(violation);
        return list;
    }

    public static List<Violation> addAllViolations(@Nullable List<Violation> list, List<Violation> source) {
        if (list == null) return new ArrayList<>(source);
        list.addAll(source);
        return list;
    }

    /**
     * @return validator that applies all the validators to the same value
     */
    @SafeVarargs
    public static <T> Validator<T> all(Validator<? super T>... validators) {
        return new AllValidator<>(validators);
    }

    private static final class AllValidator<T> implements Validator<T> {
        private final Validator<? super T>[] validators;

        AllValidator(Validator<? super T>[] validators) {
            this.validators = validators;
        }

        @Override
        public List<Violation> validate(T value, ValidationContext context) {
            var violations = new ArrayList<Violation>();
            for (var validator : validators) {
                violations.addAll(validator.validate(value, context));
                if (context.isFailFast() && !violations.isEmpty()) {
                    return violations;
                }
            }
            return violations;
        }
    }
}

package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

final class IterableValidator<T, I extends Iterable<? extends T>> implements Validator<I> {

    private final Validator<? super T> validator;

    IterableValidator(Validator<? super T> validator) {
        this.validator = validator;
    }

    public List<Violation> validate(I iterable, ValidationContext context) {
        if (iterable != null) {
            final List<Violation> violations = new ArrayList<>();
            final Iterator<? extends T> iterator = iterable.iterator();
            int i = 0;

            while (iterator.hasNext()) {
                final T t = iterator.next();
                violations.addAll(validator.validate(t, context.addPath(i++)));
                if (context.isFailFast() && !violations.isEmpty()) {
                    return violations;
                }
            }

            return violations;
        }

        return Collections.emptyList();
    }
}

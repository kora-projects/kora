package ru.tinkoff.kora.validation.common.constraint;

import ru.tinkoff.kora.validation.common.ValidationContext;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.Violation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

final class IterableValidator<T, I extends Iterable<T>> implements Validator<I> {

    private final Validator<T> validator;

    IterableValidator(Validator<T> validator) {
        this.validator = validator;
    }

    @Nonnull
    public List<Violation> validate(I iterable, @Nonnull ValidationContext context) {
        if (iterable != null) {
            final List<Violation> violations = new ArrayList<>();
            final Iterator<T> iterator = iterable.iterator();
            int i = 0;

            while (iterator.hasNext()) {
                final T t = iterator.next();
                violations.addAll(validator.validate(t, context.addPath(i++)));
            }

            return violations;
        }

        return Collections.emptyList();
    }
}

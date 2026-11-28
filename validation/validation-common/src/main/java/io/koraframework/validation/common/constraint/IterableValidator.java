package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class IterableValidator<T, I extends Iterable<? extends T>> implements Validator<I> {

    private final Validator<? super T> validator;

    IterableValidator(Validator<? super T> validator) {
        this.validator = validator;
    }

    @Override
    public List<Violation> validate(I iterable, ValidationContext context) {
        if (iterable == null) {
            return Collections.emptyList();
        }

        List<Violation> violations = null;
        var iterator = iterable.iterator();
        var i = 0;

        while (iterator.hasNext()) {
            var t = iterator.next();
            var innerViolations = validator.validate(t, context.addPath(i++));

            if (!innerViolations.isEmpty()) {
                if (violations == null) {
                    violations = new ArrayList<>(innerViolations);
                } else {
                    violations.addAll(innerViolations);
                }

                if (context.isFailFast()) {
                    return violations;
                }
            }
        }

        return violations == null ? Collections.emptyList() : violations;
    }
}

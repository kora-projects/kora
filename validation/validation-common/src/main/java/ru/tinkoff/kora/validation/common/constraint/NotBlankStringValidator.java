package ru.tinkoff.kora.validation.common.constraint;

import javax.annotation.Nonnull;
import ru.tinkoff.kora.validation.common.ValidationContext;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.Violation;

import java.util.Collections;
import java.util.List;

final class NotBlankStringValidator<T extends CharSequence> implements Validator<T> {

    @Nonnull
    @Override
    public List<Violation> validate(T value, @Nonnull ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Should be not blank, but was null"));
        } else if (value.isEmpty()) {
            return List.of(context.violates("Should be not blank, but was empty"));
        } else if (value.toString().isBlank()) {
            return List.of(context.violates("Should be not blank, but was blank"));
        }

        return Collections.emptyList();
    }
}

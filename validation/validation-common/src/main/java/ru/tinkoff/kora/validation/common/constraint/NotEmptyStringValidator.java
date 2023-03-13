package ru.tinkoff.kora.validation.common.constraint;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;
import ru.tinkoff.kora.validation.common.ValidationContext;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.Violation;

import java.util.Collections;
import java.util.List;

final class NotEmptyStringValidator<T extends CharSequence> implements Validator<T> {

    @Nonnull
    @Override
    public @NotNull List<Violation> validate(T value, @Nonnull ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Should be not empty, but was null"));
        } else if (value.isEmpty()) {
            return List.of(context.violates("Should be not empty, but was empty"));
        }

        return Collections.emptyList();
    }
}

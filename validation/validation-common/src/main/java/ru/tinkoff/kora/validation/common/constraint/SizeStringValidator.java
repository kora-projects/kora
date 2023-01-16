package ru.tinkoff.kora.validation.common.constraint;

import javax.annotation.Nonnull;
import ru.tinkoff.kora.validation.common.ValidationContext;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.Violation;

import java.util.Collections;
import java.util.List;

final class SizeStringValidator<T extends CharSequence> implements Validator<T> {

    private final int from;
    private final int to;

    public SizeStringValidator(int from, int to) {
        if (from < 0)
            throw new IllegalArgumentException("From can't be less 0, but was: " + from);

        this.from = from;
        this.to = to;
    }

    @Nonnull
    @Override
    public List<Violation> validate(T value, @Nonnull ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Length should be in range from '" + from + "' to '" + to + "', but was null"));
        } else if (value.length() < from) {
            return List.of(context.violates("Length should be in range from '" + from + "' to '" + to + "', but was smaller: " + value.length()));
        } else if (value.length() > to) {
            return List.of(context.violates("Length should be in range from '" + from + "' to '" + to + "', but was greater: " + value.length()));
        }

        return Collections.emptyList();
    }
}

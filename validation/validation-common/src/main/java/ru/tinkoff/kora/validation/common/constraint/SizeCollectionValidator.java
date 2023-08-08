package ru.tinkoff.kora.validation.common.constraint;

import ru.tinkoff.kora.validation.common.ValidationContext;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.Violation;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class SizeCollectionValidator<V, T extends Collection<V>> implements Validator<T> {

    private final int from;
    private final int to;

    public SizeCollectionValidator(int from, int to) {
        if (from < 0)
            throw new IllegalArgumentException("From can't be less 0, but was: " + from);
        if (to < from)
            throw new IllegalArgumentException("From can't be less than To, but From was " + from + " and To was " + to);

        this.from = from;
        this.to = to;
    }

    @Nonnull
    @Override
    public List<Violation> validate(T value, @Nonnull ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Size should be in range from '" + from + "' to '" + to + "', but value was null"));
        } else if (value.size() < from) {
            return List.of(context.violates("Size should be in range from '" + from + "' to '" + to + "', but was smaller: " + value.size()));
        } else if (value.size() > to) {
            return List.of(context.violates("Size should be in range from '" + from + "' to '" + to + "', but was greater: " + value.size()));
        }

        return Collections.emptyList();
    }
}

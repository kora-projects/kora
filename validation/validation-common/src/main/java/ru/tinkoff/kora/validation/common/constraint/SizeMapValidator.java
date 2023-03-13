package ru.tinkoff.kora.validation.common.constraint;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;
import ru.tinkoff.kora.validation.common.ValidationContext;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.Violation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

final class SizeMapValidator<K, V> implements Validator<Map<K, V>> {

    private final int from;
    private final int to;

    public SizeMapValidator(int from, int to) {
        if (from < 0)
            throw new IllegalArgumentException("From can't be less 0, but was: " + from);
        if(to < from)
            throw new IllegalArgumentException("From can't be less than To, but From was " + from + " and To was " + to);

        this.from = from;
        this.to = to;
    }

    @Nonnull
    @Override
    public @NotNull List<Violation> validate(Map<K, V> value, @Nonnull ValidationContext context) {
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

package ru.tinkoff.kora.validation.common.constraint;

import ru.tinkoff.kora.validation.common.ValidationContext;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.Violation;
import ru.tinkoff.kora.validation.common.annotation.Range;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

final class RangeDoubleNumberValidator<T extends Number> implements Validator<T> {

    private final double from;
    private final double to;
    private final Range.Boundary boundary;

    private final Predicate<T> fromPredicate;
    private final Predicate<T> toPredicate;

    RangeDoubleNumberValidator(double from, double to, Range.Boundary boundary) {
        if (to < from)
            throw new IllegalArgumentException("From can't be less than To, but From was " + from + " and To was " + to);

        this.from = from;
        this.to = to;
        this.boundary = boundary;
        this.fromPredicate = switch (boundary) {
            case INCLUSIVE_INCLUSIVE, INCLUSIVE_EXCLUSIVE -> (v -> v.doubleValue() >= from);
            case EXCLUSIVE_INCLUSIVE, EXCLUSIVE_EXCLUSIVE -> (v -> v.doubleValue() > from);
        };

        this.toPredicate = switch (boundary) {
            case INCLUSIVE_EXCLUSIVE, EXCLUSIVE_EXCLUSIVE -> (v -> v.doubleValue() < to);
            case EXCLUSIVE_INCLUSIVE, INCLUSIVE_INCLUSIVE -> (v -> v.doubleValue() <= to);
        };
    }

    @Nonnull
    @Override
    public List<Violation> validate(T value, @Nonnull ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Should be in range from '" + from + "' to '" + to + "', but was null"));
        }

        if (!fromPredicate.test(value)) {
            return List.of(context.violates("Should be in range from '" + from + "' to '" + to + "', but was smaller: " + value));
        } else if (!toPredicate.test(value)) {
            return List.of(context.violates("Should be in range from '" + from + "' to '" + to + "', but was greater: " + value));
        }

        return Collections.emptyList();
    }
}

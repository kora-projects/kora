package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Collections;
import java.util.List;

final class SizeStringValidator<T extends CharSequence> implements Validator<T> {

    private final int from;
    private final int to;

    public SizeStringValidator(int from, int to) {
        if (from < 0)
            throw new IllegalArgumentException("Invalid size range: from must be >= 0, got " + from);
        if (to < from)
            throw new IllegalArgumentException("Invalid size range: to must be >= from, got from=" + from + ", to=" + to);

        this.from = from;
        this.to = to;
    }

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Length should be in range from '" + from + "' to '" + to + "', but was null"));
        }

        int length = Character.codePointCount(value, 0, value.length());
        if (length < from) {
            return List.of(context.violates("Length should be in range from '" + from + "' to '" + to + "', but was smaller: " + length));
        } else if (length > to) {
            return List.of(context.violates("Length should be in range from '" + from + "' to '" + to + "', but was greater: " + length));
        }

        return Collections.emptyList();
    }
}

package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Collections;
import java.util.List;
import java.util.Map;

final class SizeMapValidator<K, V> implements Validator<Map<K, V>> {

    private final int from;
    private final int to;

    public SizeMapValidator(int from, int to) {
        if (from < 0)
            throw new IllegalArgumentException("Invalid size range: from must be >= 0, got " + from);
        if (to < from)
            throw new IllegalArgumentException("Invalid size range: to must be >= from, got from=" + from + ", to=" + to);

        this.from = from;
        this.to = to;
    }

    @Override
    public List<Violation> validate(Map<K, V> value, ValidationContext context) {
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

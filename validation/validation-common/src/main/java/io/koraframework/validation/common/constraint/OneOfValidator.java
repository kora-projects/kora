package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class OneOfValidator<T extends CharSequence> implements Validator<T> {

    private final Set<String> values;

    OneOfValidator(String[] values) {
        this.values = Arrays.stream(values).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Should be one of " + values + ", but was null"));
        } else if (!values.contains(value.toString())) {
            return List.of(context.violates("Should be one of " + values + ", but was: " + value));
        }

        return Collections.emptyList();
    }
}

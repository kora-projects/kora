package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

final class UriValidator<T extends CharSequence> implements Validator<T> {
    private static final String MSG_NULL = "Should be valid URI, but was null";
    private static final String MSG_PREFIX_INVALID = "Should be valid URI, but was: ";

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(MSG_NULL));
        }

        try {
            new URI(value.toString());
        } catch (URISyntaxException e) {
            return List.of(context.violates(MSG_PREFIX_INVALID + value));
        }

        return Collections.emptyList();
    }
}

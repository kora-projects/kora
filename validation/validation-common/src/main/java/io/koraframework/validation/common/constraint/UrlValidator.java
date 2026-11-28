package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

final class UrlValidator<T extends CharSequence> implements Validator<T> {
    private static final String MSG_NULL = "Should be valid URL, but was null";
    private static final String MSG_PREFIX_INVALID = "Should be valid URL, but was: ";

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(MSG_NULL));
        }

        try {
            var uri = new URI(value.toString());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return List.of(context.violates(MSG_PREFIX_INVALID + value));
            }
        } catch (URISyntaxException e) {
            return List.of(context.violates(MSG_PREFIX_INVALID + value));
        }

        return Collections.emptyList();
    }
}

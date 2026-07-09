package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

final class UriValidator<T extends CharSequence> implements Validator<T> {

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Should be valid URI, but was null"));
        }

        try {
            new URI(value.toString());
        } catch (URISyntaxException e) {
            return List.of(context.violates("Should be valid URI, but was: " + value));
        }

        return Collections.emptyList();
    }
}

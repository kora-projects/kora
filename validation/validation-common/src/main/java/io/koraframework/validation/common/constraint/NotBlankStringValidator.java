package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.ValidationContext;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.Violation;

import java.util.Collections;
import java.util.List;

final class NotBlankStringValidator<T extends CharSequence> implements Validator<T> {
    private static final String MSG_NULL = "Should be not blank, but was null";
    private static final String MSG_EMPTY = "Should be not blank, but was empty";
    private static final String MSG_BLANK = "Should be not blank, but was blank";

    @Override
    public List<Violation> validate(T value, ValidationContext context) {
        if (value == null) {
            return List.of(context.violates(MSG_NULL));
        } else if (value.isEmpty()) {
            return List.of(context.violates(MSG_EMPTY));
        } else if (isBlank(value)) {
            return List.of(context.violates(MSG_BLANK));
        }

        return Collections.emptyList();
    }

    private static boolean isBlank(CharSequence cs) {
        var strLen = cs.length();
        if (strLen == 0) {
            return true;
        }
        for (var i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}

package ru.tinkoff.kora.validation.common.constraint;

import javax.annotation.Nonnull;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import ru.tinkoff.kora.validation.common.ValidationContext;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.Violation;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

final class PatternValidator<T extends CharSequence> implements Validator<T> {

    private final Pattern pattern;

    PatternValidator(@Language("RegExp") String pattern, int flags) {
        this.pattern = Pattern.compile(pattern, flags);
    }

    @Nonnull
    @Override
    public @NotNull List<Violation> validate(T value, @Nonnull ValidationContext context) {
        if (value == null) {
            return List.of(context.violates("Should match RegEx " + pattern + " but was null"));
        } else if (!pattern.matcher(value).find()) {
            return List.of(context.violates("Should match RegEx " + pattern + " but was: " + value));
        }

        return Collections.emptyList();
    }
}

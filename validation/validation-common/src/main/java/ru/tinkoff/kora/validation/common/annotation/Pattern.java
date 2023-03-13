package ru.tinkoff.kora.validation.common.annotation;

import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.validation.common.constraint.factory.PatternValidatorFactory;

import java.lang.annotation.*;

/**
 * Validates that {@link String} or {@link CharSequence} matches RegEx
 */
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(PatternValidatorFactory.class)
public @interface Pattern {

    /**
     * @return RegEx pattern
     */
    @Language("RegExp")
    String value();

    /**
     * @return {@link java.util.regex.Pattern#flags()} for RegEx
     * @see java.util.regex.Pattern#CASE_INSENSITIVE
     * @see java.util.regex.Pattern#MULTILINE
     * @see java.util.regex.Pattern#DOTALL
     * @see java.util.regex.Pattern#UNICODE_CASE
     * @see java.util.regex.Pattern#CANON_EQ
     * @see java.util.regex.Pattern#UNIX_LINES
     * @see java.util.regex.Pattern#LITERAL
     * @see java.util.regex.Pattern#UNICODE_CHARACTER_CLASS
     * @see java.util.regex.Pattern#COMMENTS
     */
    int flags() default 0;
}

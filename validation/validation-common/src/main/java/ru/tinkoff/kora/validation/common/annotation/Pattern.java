package ru.tinkoff.kora.validation.common.annotation;

import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.validation.common.constraint.factory.PatternValidatorFactory;

import java.lang.annotation.*;

@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.FIELD})
@ValidatedBy(PatternValidatorFactory.class)
public @interface Pattern {

    /**
     * @return RegEx pattern
     */
    @Language("RegExp")
    String value();

    /**
     * @return {@link java.util.regex.Pattern#flags()} for RegEx
     */
    int flags() default 0;
}

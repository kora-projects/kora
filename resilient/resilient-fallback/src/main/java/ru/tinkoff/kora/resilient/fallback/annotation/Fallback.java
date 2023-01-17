package ru.tinkoff.kora.resilient.fallback.annotation;

import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.common.AopAnnotation;

import java.lang.annotation.*;

@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface Fallback {

    /**
     * @return configuration name
     */
    String value();

    /**
     * @return fallbackMethod method name to execute
     */
    @Language(value = "JAVA", prefix = "class Renderer{String $text(){return ", suffix = ";}}")
    String method();
}

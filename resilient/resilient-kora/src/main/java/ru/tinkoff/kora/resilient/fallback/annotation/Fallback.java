package ru.tinkoff.kora.resilient.fallback.annotation;

import org.intellij.lang.annotations.Language;
import ru.tinkoff.kora.common.AopAnnotation;
import ru.tinkoff.kora.resilient.fallback.FallbackConfig;

import java.lang.annotation.*;

@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
public @interface Fallback {

    /**
     * @see FallbackConfig
     * @return the name of Fallback config path
     */
    String value();

    /**
     * @return fallbackMethod method name to execute
     */
    @Language(value = "JAVA", prefix = "class Renderer{String $text(){return ", suffix = ";}}")
    String method();
}

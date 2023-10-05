package ru.tinkoff.kora.http.client.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HttpClient {
    String configPath() default "";

    Class<?>[] telemetryTag() default {};

    Class<?>[] httpClientTag() default {};
}

package ru.tinkoff.kora.http.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает аргумент метода должен быть интерпретирован как Cookie параметр запроса/ответа
 * <hr>
 * <b>English</b>: Annotation specifies the method argument should be interpreted as a Cookie request/response parameter
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @HttpClient("my.config")
 * public interface MyHttpClient {
 *
 *     @HttpRoute(method = "GET", path = "/username")
 *     String getUserCode(@Cookie("name") String value);
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Cookie {

    /**
     * @return <b>Русский</b>: Описывает имя Cookie параметра
     * <hr>
     * <b>English</b>: Describes the name of the Cookie parameter
     */
    String value() default "";
}

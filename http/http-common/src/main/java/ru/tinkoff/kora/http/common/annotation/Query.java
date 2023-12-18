package ru.tinkoff.kora.http.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает аргумент метода должен быть интерпретирован как параметр запроса/ответа
 * <hr>
 * <b>English</b>: Annotation specifies the method argument should be interpreted as a request/response query parameter
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @HttpClient("my.config")
 * public interface MyHttpClient {
 *
 *     @HttpRoute(method = "GET", path = "/username")
 *     String getUserCode(@Query("name") String value);
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Query {

    /**
     * @return <b>Русский</b>: Описывает имя параметра запроса
     * <hr>
     * <b>English</b>: Describes the name of the Query parameter
     */
    String value() default "";
}

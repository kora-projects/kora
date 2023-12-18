package ru.tinkoff.kora.http.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает аргумент метода должен быть интерпретирован как часть пути запроса/ответа
 * <hr>
 * <b>English</b>: Annotation specifies the method argument should be interpreted as a part of request/response path
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @HttpClient("my.config")
 * public interface MyHttpClient {
 *
 *     @HttpRoute(method = "GET", path = "/username/{name}")
 *     String getUserCode(@Path("name") String value);
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Path {

    /**
     * @return <b>Русский</b>: Описывает имя параметра пути
     * <hr>
     * <b>English</b>: Describes the name of the Path parameter
     */
    String value() default "";
}

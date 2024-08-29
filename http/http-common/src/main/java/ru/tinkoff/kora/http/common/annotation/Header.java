package ru.tinkoff.kora.http.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает аргумент метода должен быть интерпретирован как заголовок запроса/ответа
 * <hr>
 * <b>English</b>: Annotation specifies the method argument should be interpreted as a request/response header
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @HttpClient("my.config")
 * public interface MyHttpClient {
 *
 *     @HttpRoute(method = "GET", path = "/username")
 *     String getUserCode(@Header("name") String value);
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Header {

    /**
     * @return <b>Русский</b>: Описывает имя заголовка параметра
     * <hr>
     * <b>English</b>: Describes the name of the Header parameter
     */
    String value() default "";
}

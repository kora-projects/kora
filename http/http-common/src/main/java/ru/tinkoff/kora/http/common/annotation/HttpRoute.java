package ru.tinkoff.kora.http.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что метод является HTTP операцией и определяет ее путь и тип
 * <hr>
 * <b>English</b>: Annotation specifies that the method is an HTTP operation and defines its path and type
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @HttpClient("my.config")
 * public interface MyHttpClient {
 *
 *     @HttpRoute(method = "GET", path = "/username")
 *     String getUserCode();
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface HttpRoute {

    /**
     * @return <b>Русский</b>: Описывает метод HTTP обработчика
     * <hr>
     * <b>English</b>: Describes HTTP controller method
     * @see ru.tinkoff.kora.http.common.HttpMethod
     */
    String method();

    /**
     * @return <b>Русский</b>: Описывает путь HTTP обработчика
     * <hr>
     * <b>English</b>: Describes HTTP controller path
     */
    String path();
}

package ru.tinkoff.kora.http.server.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает класс содержит HTTP обработчики
 * <hr>
 * <b>English</b>: Annotation specifies the class contains HTTP handlers
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @HttpController
 * public class Controller {
 *
 *     @HttpRoute(method = GET, path = "/pets/status")
 *     public String getPets() {
 *        return "OK";
 *     }
 * }
 * }
 * </pre>
 * @see ru.tinkoff.kora.http.common.annotation.HttpRoute
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpController {

    /**
     * @return <b>Русский</b>: Описывает префикс пути HTTP обработчиков
     * <hr>
     * <b>English</b>: Describes the path prefix of HTTP handlers
     */
    String value() default "";
}

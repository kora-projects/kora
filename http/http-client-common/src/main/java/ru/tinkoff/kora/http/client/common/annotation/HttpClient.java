package ru.tinkoff.kora.http.client.common.annotation;

import ru.tinkoff.kora.common.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <b>Русский</b>: Аннотация указывает что интерфейс является HTTP клиентом и отвечает за взаимодействие с внешним сервисом.
 * <hr>
 * <b>English</b>: The annotation indicates that the interface is an HTTP client and is responsible for interacting with an external service.
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @HttpClient("my.config")
 * public interface MyHttpclient {
 *
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HttpClient {

    /**
     * @return <b>Русский</b>: Указывает путь конфигурации HTTP клиента
     * <hr>
     * <b>English</b>: Specifies the configuration path of the HTTP client
     */
    String configPath() default "";

    /**
     * @return <b>Русский</b>: Теги {@link Tag} для собственной телеметрии
     * <hr>
     * <b>English</b>: Tags {@link Tag} for your own telemetry
     */
    Class<?> telemetryTag() default Tag.class;

    Class<?> httpClientTag() default Tag.class;
}

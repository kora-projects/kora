package ru.tinkoff.kora.http.common.annotation;

import ru.tinkoff.kora.common.Tag;

import java.lang.annotation.*;

/**
 * <b>Русский</b>: Аннотация указывает для класса/метода/HTTP-сервера будет зарегистрирован перехватчик
 * <hr>
 * <b>English</b>: Annotation specifies for the class/method/HTTP server the interceptor will be registered for
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @HttpClient("my.config")
 * public interface MyHttpClient {
 *
 *     @InterceptWith(HttpClientInterceptor.class)
 *     @HttpRoute(method = "GET", path = "/username")
 *     String getUserCode();
 * }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(InterceptWith.InterceptWithContainer.class)
public @interface InterceptWith {

    /**
     * @return <b>Русский</b>: Указывает реализацию HTTP перехватчика
     * <hr>
     * <b>English</b>: Specifies the implementation of the HTTP interceptor
     */
    Class<?> value();

    Tag tag() default @Tag(Tag.class);

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @interface InterceptWithContainer {
        InterceptWith[] value();
    }
}

package ru.tinkoff.kora.http.client.common.annotation;

import org.jetbrains.annotations.Range;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;

import java.lang.annotation.*;

/**
 * <b>Русский</b>: Аннотация позволяет указывать обработчики HTTP ответов на определенные HTTP статус коды
 * <hr>
 * <b>English</b>: Annotation allows you to specify HTTP response handlers for specific HTTP status codes
 * <br>
 * <br>
 * Пример / Example:
 * <pre>
 * {@code
 * @HttpClient("my.config")
 * public interface MyHttpclient {
 *
 *     @ResponseCodeMapper(code = DEFAULT, mapper = MyOtherMapper.class)
 *     @ResponseCodeMapper(code = 200, mapper = MyMapper.class)
 *     @HttpRoute(method = HttpMethod.POST, path = "/users/status")
 *     void postStatus();
 * }
 * }
 * </pre>
 *
 * @see HttpClient
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(ResponseCodeMapper.ResponseCodeMappers.class)
public @interface ResponseCodeMapper {

    /**
     * <b>Русский</b>: Указывает что обработчик будет действовать для любого HTTP статус кода за исключением других указанных {@link ResponseCodeMapper}
     * <hr>
     * <b>English</b>: Specifies that the handler will act on any HTTP status code except for other specified {@link ResponseCodeMapper}
     */
    int DEFAULT = -1;

    /**
     * @return <b>Русский</b>: Указывает для какого HTTP статус кода зарегистрировать обработчик
     * <hr>
     * <b>English</b>: Specifies for which HTTP status code to register the handler
     */
    int code();

    Class<?> type() default Object.class;

    /**
     * @return <b>Русский</b>: Указывает реализацию обработчика который требуется зарегистрировать
     * <hr>
     * <b>English</b>: Specifies the implementation of the handler to be registered
     */
    Class<? extends HttpClientResponseMapper> mapper() default HttpClientResponseMapper.class;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ResponseCodeMappers {
        ResponseCodeMapper[] value();
    }
}

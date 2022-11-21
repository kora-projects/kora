package ru.tinkoff.kora.http.client.common.annotation;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
@Repeatable(ResponseCodeMapper.ResponseCodeMappers.class)
public @interface ResponseCodeMapper {
    int DEFAULT = -1;

    int code();

    Class<?> type() default Object.class;

    Class<? extends HttpClientResponseMapper<?, ?>> mapper() default DefaultHttpClientResponseMapper.class;

    class DefaultHttpClientResponseMapper implements HttpClientResponseMapper<Object, Mono<Object>> {

        @Override
        public Mono<Object> apply(HttpClientResponse response) {
            return null;
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.SOURCE)
    @interface ResponseCodeMappers {
        ResponseCodeMapper[] value();
    }
}

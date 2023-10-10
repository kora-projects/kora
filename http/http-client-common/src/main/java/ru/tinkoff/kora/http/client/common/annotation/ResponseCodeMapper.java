package ru.tinkoff.kora.http.client.common.annotation;

import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapper;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(ResponseCodeMapper.ResponseCodeMappers.class)
public @interface ResponseCodeMapper {

    int DEFAULT = -1;

    int code();

    Class<?> type() default Object.class;

    @SuppressWarnings("rawtypes")
    Class<? extends HttpClientResponseMapper> mapper() default DefaultHttpClientResponseMapper.class;

    class DefaultHttpClientResponseMapper implements HttpClientResponseMapper<Object> {

        @Override
        public Object apply(HttpClientResponse response) {
            return null;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ResponseCodeMappers {
        ResponseCodeMapper[] value();
    }
}

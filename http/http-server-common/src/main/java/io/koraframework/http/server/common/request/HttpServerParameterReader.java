package io.koraframework.http.server.common.request;

import io.koraframework.http.server.common.response.HttpServerResponseException;

import java.util.function.Function;

public interface HttpServerParameterReader<T> {

    T read(String string);

    static <T> HttpServerParameterReader<T> of(Function<String, T> converter, String errorMessage) {
        return string -> {
            try {
                return converter.apply(string);
            } catch (Exception e) {
                throw HttpServerResponseException.of(e, 400, errorMessage);
            }
        };
    }

    static <T> HttpServerParameterReader<T> of(Function<String, T> converter, Function<String, String> errorMessage) {
        return string -> {
            try {
                return converter.apply(string);
            } catch (Exception e) {
                throw HttpServerResponseException.of(e, 400, errorMessage.apply(string));
            }
        };
    }
}

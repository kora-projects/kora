package ru.tinkoff.kora.http.server.common.handler;

import ru.tinkoff.kora.http.server.common.HttpServerResponseException;

import java.util.function.Function;

public interface StringParameterReader<T> {
    T read(String string);

    static <T> StringParameterReader<T> of(Function<String, T> converter, String errorMessage) {
        return string -> {
            try {
                return converter.apply(string);
            } catch (Exception e) {
                throw HttpServerResponseException.of(e, 400, errorMessage);
            }
        };
    }

    static <T> StringParameterReader<T> of(Function<String, T> converter, Function<String, String> errorMessage) {
        return string -> {
            try {
                return converter.apply(string);
            } catch (Exception e) {
                throw HttpServerResponseException.of(e, 400, errorMessage.apply(string));
            }
        };
    }
}

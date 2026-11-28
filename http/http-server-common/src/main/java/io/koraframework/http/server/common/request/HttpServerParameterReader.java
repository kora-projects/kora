package io.koraframework.http.server.common.request;

import io.koraframework.http.server.common.response.HttpServerResponseException;

import java.util.function.Function;

public interface HttpServerParameterReader<T> {

    T read(String string);

    static <T> HttpServerParameterReader<T> of(Function<String, T> converter, String errorMessage) {
        return new ConverterStringMessageReader<>(converter, errorMessage);
    }

    static <T> HttpServerParameterReader<T> of(Function<String, T> converter, Function<String, String> errorMessage) {
        return new ConverterFunctionMessageReader<>(converter, errorMessage);
    }

    final class ConverterStringMessageReader<T> implements HttpServerParameterReader<T> {
        private final Function<String, T> converter;
        private final String errorMessage;

        public ConverterStringMessageReader(Function<String, T> converter, String errorMessage) {
            this.converter = converter;
            this.errorMessage = errorMessage;
        }

        @Override
        public T read(String string) {
            try {
                return converter.apply(string);
            } catch (Exception e) {
                throw HttpServerResponseException.of(e, 400, errorMessage);
            }
        }
    }

    final class ConverterFunctionMessageReader<T> implements HttpServerParameterReader<T> {
        private final Function<String, T> converter;
        private final Function<String, String> errorMessage;

        public ConverterFunctionMessageReader(Function<String, T> converter, Function<String, String> errorMessage) {
            this.converter = converter;
            this.errorMessage = errorMessage;
        }

        @Override
        public T read(String string) {
            try {
                return converter.apply(string);
            } catch (Exception e) {
                throw HttpServerResponseException.of(e, 400, errorMessage.apply(string));
            }
        }
    }
}

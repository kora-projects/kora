package io.koraframework.http.client.common.writer;

public interface StringParameterConverter<T> {
    String convert(T value);
}

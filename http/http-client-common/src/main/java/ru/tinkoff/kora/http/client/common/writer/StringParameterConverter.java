package ru.tinkoff.kora.http.client.common.writer;

public interface StringParameterConverter<T> {
    String convert(T object);
}

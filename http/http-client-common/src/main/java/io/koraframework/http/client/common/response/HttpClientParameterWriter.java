package io.koraframework.http.client.common.response;

public interface HttpClientParameterWriter<T> {
    String convert(T value);
}

package io.koraframework.http.client.common.request;

public interface HttpClientParameterWriter<T> {
    String convert(T value);
}

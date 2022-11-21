package ru.tinkoff.kora.http.client.common.request;

import ru.tinkoff.kora.common.Mapping;

public interface HttpClientRequestMapper<T> extends Mapping.MappingFunction {
    record Request<T>(HttpClientRequestBuilder builder, T parameter) {}

    HttpClientRequestBuilder apply(Request<T> request);
}

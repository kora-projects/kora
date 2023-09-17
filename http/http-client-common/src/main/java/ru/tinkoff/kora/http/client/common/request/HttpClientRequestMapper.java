package ru.tinkoff.kora.http.client.common.request;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.Mapping;

public interface HttpClientRequestMapper<T> extends Mapping.MappingFunction {
    HttpClientRequestBuilder apply(Context ctx, HttpClientRequestBuilder builder, T value) throws Exception;
}

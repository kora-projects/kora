package ru.tinkoff.kora.http.client.common.request;

import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

public interface HttpClientRequestMapper<T> extends Mapping.MappingFunction {
    HttpBodyOutput apply(T value) throws Exception;
}

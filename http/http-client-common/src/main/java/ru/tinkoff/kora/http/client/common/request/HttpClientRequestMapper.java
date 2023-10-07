package ru.tinkoff.kora.http.client.common.request;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;

public interface HttpClientRequestMapper<T> extends Mapping.MappingFunction {
    HttpBodyOutput apply(Context ctx, T value) throws Exception;
}

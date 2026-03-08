package io.koraframework.http.client.common.request;

import io.koraframework.common.Mapping;
import io.koraframework.http.common.body.HttpBodyOutput;

public interface HttpClientRequestMapper<T> extends Mapping.MappingFunction {
    HttpBodyOutput apply(T value) throws Exception;
}

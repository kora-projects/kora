package io.koraframework.http.client.common.response;

import org.jspecify.annotations.Nullable;
import io.koraframework.common.Mapping;
import io.koraframework.http.client.common.exception.HttpClientDecoderException;

import java.io.IOException;

public interface HttpClientResponseMapper<T> extends Mapping.MappingFunction {

    @Nullable
    T apply(HttpClientResponse response) throws IOException, HttpClientDecoderException;
}

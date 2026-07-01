package io.koraframework.http.client.common.response;

import io.koraframework.common.annotation.Mapping;
import io.koraframework.http.client.common.exception.HttpClientDecoderException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public interface HttpClientResponseMapper<T> extends Mapping.MappingFunction {

    @Nullable
    T apply(HttpClientResponse response) throws IOException, HttpClientDecoderException;
}

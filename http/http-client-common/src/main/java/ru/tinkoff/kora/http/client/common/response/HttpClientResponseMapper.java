package ru.tinkoff.kora.http.client.common.response;

import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.Mapping;
import ru.tinkoff.kora.http.client.common.HttpClientDecoderException;

import java.io.IOException;

public interface HttpClientResponseMapper<T> extends Mapping.MappingFunction {

    @Nullable
    T apply(HttpClientResponse response) throws IOException, HttpClientDecoderException;
}

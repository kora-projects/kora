package io.koraframework.json.jackson.module;

import io.koraframework.application.graph.TypeRef;
import io.koraframework.common.Either;
import io.koraframework.http.client.common.request.HttpClientRequestMapper;
import io.koraframework.http.client.common.response.HttpClientResponseMapper;
import io.koraframework.http.client.common.response.mapper.HttpClientEitherResponseMapper;
import io.koraframework.http.common.HttpResponseEntity;
import io.koraframework.http.server.common.request.HttpServerRequestMapper;
import io.koraframework.http.server.common.response.HttpServerResponseMapper;
import io.koraframework.http.server.common.response.mapper.HttpServerResponseEntityMapper;
import io.koraframework.json.common.annotation.Json;
import io.koraframework.json.jackson.module.http.client.JacksonHttpClientRequestMapper;
import io.koraframework.json.jackson.module.http.client.JacksonHttpClientResponseMapper;
import io.koraframework.json.jackson.module.http.server.JacksonHttpServerRequestMapper;
import io.koraframework.json.jackson.module.http.server.JacksonHttpServerResponseMapper;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;

public interface JacksonModule {

    @Json
    default <T> HttpServerRequestMapper<T> jacksonHttpServerRequestMapper(ObjectMapper objectMapper, TypeRef<T> type) {
        return new JacksonHttpServerRequestMapper<>(objectMapper, type);
    }

    @Json
    @SuppressWarnings({"unchecked", "rawtypes"})
    default <T> HttpServerResponseMapper<T> jacksonHttpServerResponseMapper(ObjectMapper objectMapper, TypeRef<T> type) {
        // This generic factory shadows the default HttpResponseEntity mapper, so it has to unwrap the entity itself
        if (isWrapper(type, HttpResponseEntity.class, 1)) {
            return (HttpServerResponseMapper<T>) new HttpServerResponseEntityMapper<>(jacksonHttpServerResponseMapper(objectMapper, typeArgument(type, 0)));
        }
        return new JacksonHttpServerResponseMapper<>(objectMapper, type);
    }

    @Json
    default <T> HttpClientRequestMapper<T> jacksonHttpClientRequestMapper(ObjectMapper objectMapper, TypeRef<T> typeRef) {
        return new JacksonHttpClientRequestMapper<>(objectMapper, typeRef);
    }

    @Json
    @SuppressWarnings({"unchecked", "rawtypes"})
    default <T> HttpClientResponseMapper<T> jacksonHttpClientResponseMapper(ObjectMapper objectMapper, TypeRef<T> typeRef) {
        // This generic factory shadows the default HttpResponseEntity and Either mappers, so it has to unwrap them itself
        if (isWrapper(typeRef, HttpResponseEntity.class, 1)) {
            var delegate = jacksonHttpClientResponseMapper(objectMapper, typeArgument(typeRef, 0));
            return response -> (T) HttpResponseEntity.of(response.code(), response.headers(), delegate.apply(response));
        }
        if (isWrapper(typeRef, Either.class, 2)) {
            return (HttpClientResponseMapper<T>) new HttpClientEitherResponseMapper<>(
                jacksonHttpClientResponseMapper(objectMapper, typeArgument(typeRef, 0)),
                jacksonHttpClientResponseMapper(objectMapper, typeArgument(typeRef, 1))
            );
        }
        return new JacksonHttpClientResponseMapper<>(objectMapper, typeRef);
    }

    private static boolean isWrapper(TypeRef<?> type, Class<?> wrapper, int arguments) {
        return type.getRawType() == wrapper && type.getActualTypeArguments().length == arguments;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static TypeRef<Object> typeArgument(TypeRef<?> type, int index) {
        Type argument = type.getActualTypeArguments()[index];
        return argument instanceof TypeRef<?> ref
            ? (TypeRef<Object>) ref
            : TypeRef.of((Class) argument);
    }
}

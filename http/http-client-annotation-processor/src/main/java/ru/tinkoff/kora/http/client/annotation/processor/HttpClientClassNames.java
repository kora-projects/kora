package ru.tinkoff.kora.http.client.annotation.processor;

import com.squareup.javapoet.ClassName;

public class HttpClientClassNames {
    public static final ClassName httpClient = ClassName.get("ru.tinkoff.kora.http.client.common", "HttpClient");
    public static final ClassName httpClientAnnotation = ClassName.get("ru.tinkoff.kora.http.client.common.annotation", "HttpClient");
    public static final ClassName httpClientException = ClassName.get("ru.tinkoff.kora.http.client.common", "HttpClientException");
    public static final ClassName httpClientEncoderException = ClassName.get("ru.tinkoff.kora.http.client.common", "HttpClientEncoderException");
    public static final ClassName httpClientResponseException = ClassName.get("ru.tinkoff.kora.http.client.common", "HttpClientResponseException");
    public static final ClassName responseCodeMapper = ClassName.get("ru.tinkoff.kora.http.client.common.annotation", "ResponseCodeMapper");
    public static final ClassName responseCodeMappers = responseCodeMapper.nestedClass("ResponseCodeMappers");
    public static final ClassName httpClientRequestBuilder = ClassName.get("ru.tinkoff.kora.http.client.common.request", "HttpClientRequestBuilder");
    public static final ClassName uriQueryBuilder = ClassName.get("ru.tinkoff.kora.http.client.common.request", "UriQueryBuilder");
    public static final ClassName httpClientRequest = ClassName.get("ru.tinkoff.kora.http.client.common.request", "HttpClientRequest");
    public static final ClassName httpBody = ClassName.get("ru.tinkoff.kora.http.common.body", "HttpBody");
    public static final ClassName httpBodyOutput = ClassName.get("ru.tinkoff.kora.http.common.body", "HttpBodyOutput");
    public static final ClassName httpClientRequestMapper = ClassName.get("ru.tinkoff.kora.http.client.common.request", "HttpClientRequestMapper");
    public static final ClassName httpClientResponseMapper = ClassName.get("ru.tinkoff.kora.http.client.common.response", "HttpClientResponseMapper");
    public static final ClassName httpClientTelemetryFactory = ClassName.get("ru.tinkoff.kora.http.client.common.telemetry", "HttpClientTelemetryFactory");
    public static final ClassName stringParameterConverter = ClassName.get("ru.tinkoff.kora.http.client.common.writer", "StringParameterConverter");
    public static final ClassName httpHeaders = ClassName.get("ru.tinkoff.kora.http.common.header", "HttpHeaders");
    public static final ClassName httpRoute = ClassName.get("ru.tinkoff.kora.http.common.annotation", "HttpRoute");
    public static final ClassName httpClientUnknownException = ClassName.get("ru.tinkoff.kora.http.client.common", "HttpClientUnknownException");
    public static final ClassName httpClientOperationConfig  = ClassName.get("ru.tinkoff.kora.http.client.common.declarative", "HttpClientOperationConfig");
    public static final ClassName declarativeHttpClientConfig  = ClassName.get("ru.tinkoff.kora.http.client.common.declarative", "DeclarativeHttpClientConfig");
    public static final ClassName interceptWithClassName = ClassName.get("ru.tinkoff.kora.http.common.annotation", "InterceptWith");
    public static final ClassName interceptWithContainerClassName = ClassName.get("ru.tinkoff.kora.http.common.annotation", "InterceptWith", "InterceptWithContainer");

    public static final ClassName header = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Header");
    public static final ClassName query = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Query");
    public static final ClassName path = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Path");
}

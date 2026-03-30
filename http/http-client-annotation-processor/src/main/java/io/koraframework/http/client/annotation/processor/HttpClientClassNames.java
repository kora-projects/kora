package io.koraframework.http.client.annotation.processor;

import com.palantir.javapoet.ClassName;

public class HttpClientClassNames {

    public static final ClassName header = ClassName.get("io.koraframework.http.common.annotation", "Header");
    public static final ClassName query = ClassName.get("io.koraframework.http.common.annotation", "Query");
    public static final ClassName path = ClassName.get("io.koraframework.http.common.annotation", "Path");
    public static final ClassName cookie = ClassName.get("io.koraframework.http.common.annotation", "Cookie");
    public static final ClassName httpRoute = ClassName.get("io.koraframework.http.common.annotation", "HttpRoute");
    public static final ClassName httpResponseEntity = ClassName.get("io.koraframework.http.common", "HttpResponseEntity");
    public static final ClassName httpClient = ClassName.get("io.koraframework.http.client.common", "HttpClient");
    public static final ClassName httpHeaders = ClassName.get("io.koraframework.http.common.header", "HttpHeaders");
    public static final ClassName httpCookie = ClassName.get("io.koraframework.http.common.cookie", "Cookie");

    public static final ClassName responseCodeMapper = ClassName.get("io.koraframework.http.client.common.annotation", "ResponseCodeMapper");
    public static final ClassName responseCodeMappers = responseCodeMapper.nestedClass("ResponseCodeMappers");
    public static final ClassName httpClientAnnotation = ClassName.get("io.koraframework.http.client.common.annotation", "HttpClient");
    public static final ClassName interceptWithClassName = ClassName.get("io.koraframework.http.common.annotation", "InterceptWith");
    public static final ClassName interceptWithContainerClassName = ClassName.get("io.koraframework.http.common.annotation", "InterceptWith", "InterceptWithContainer");

    public static final ClassName httpClientException = ClassName.get("io.koraframework.http.client.common.exception", "HttpClientException");
    public static final ClassName httpClientEncoderException = ClassName.get("io.koraframework.http.client.common.exception", "HttpClientEncoderException");
    public static final ClassName httpClientResponseException = ClassName.get("io.koraframework.http.client.common.exception", "HttpClientResponseException");
    public static final ClassName httpClientUnknownException = ClassName.get("io.koraframework.http.client.common.exception", "HttpClientUnknownException");

    public static final ClassName httpBody = ClassName.get("io.koraframework.http.common.body", "HttpBody");
    public static final ClassName httpBodyOutput = ClassName.get("io.koraframework.http.common.body", "HttpBodyOutput");
    public static final ClassName httpClientRequestBuilder = ClassName.get("io.koraframework.http.client.common.request", "HttpClientRequestBuilder");
    public static final ClassName uriQueryBuilder = ClassName.get("io.koraframework.http.client.common.request", "UriQueryBuilder");
    public static final ClassName httpClientRequest = ClassName.get("io.koraframework.http.client.common.request", "HttpClientRequest");
    public static final ClassName httpClientRequestMapper = ClassName.get("io.koraframework.http.client.common.request", "HttpClientRequestMapper");
    public static final ClassName httpClientEncoderUtils = ClassName.get("io.koraframework.http.client.common.request", "EncoderUtils");
    public static final ClassName httpClientResponseMapper = ClassName.get("io.koraframework.http.client.common.response", "HttpClientResponseMapper");
    public static final ClassName stringParameterConverter = ClassName.get("io.koraframework.http.client.common.request", "HttpClientParameterWriter");
    public static final ClassName httpClientResponseEntityMapper = ClassName.get("io.koraframework.http.client.common.response.mapper", "HttpClientResponseEntityMapper");

    public static final ClassName httpClientTelemetryFactory = ClassName.get("io.koraframework.http.client.common.telemetry", "HttpClientTelemetryFactory");
    public static final ClassName httpClientOperationConfig = ClassName.get("io.koraframework.http.client.common.declarative", "HttpClientOperationConfig");
    public static final ClassName declarativeHttpClientConfig = ClassName.get("io.koraframework.http.client.common.declarative", "DeclarativeHttpClientConfig");
    public static final ClassName telemetryHttpClientConfig = ClassName.get("io.koraframework.http.client.common.telemetry", "HttpClientTelemetryConfig");

}

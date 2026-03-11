package io.koraframework.http.client.symbol.processor

import com.squareup.kotlinpoet.ClassName

object HttpClientClassNames {
    val httpClient = ClassName("io.koraframework.http.client.common", "HttpClient")
    val httpClientAnnotation = ClassName("io.koraframework.http.client.common.annotation", "HttpClient")
    val httpClientException = ClassName("io.koraframework.http.client.common", "HttpClientException")
    val httpClientEncoderException = ClassName("io.koraframework.http.client.common", "HttpClientEncoderException")
    val httpClientResponseException = ClassName("io.koraframework.http.client.common", "HttpClientResponseException")
    val responseCodeMapper = ClassName("io.koraframework.http.client.common.annotation", "ResponseCodeMapper")
    val responseCodeMappers = responseCodeMapper.nestedClass("ResponseCodeMappers")
    val httpClientRequest = ClassName("io.koraframework.http.client.common.request", "HttpClientRequest")
    val httpClientRequestMapper = ClassName("io.koraframework.http.client.common.request", "HttpClientRequestMapper")
    val httpClientResponse = ClassName("io.koraframework.http.client.common.response", "HttpClientResponse")
    val httpClientResponseMapper = ClassName("io.koraframework.http.client.common.response", "HttpClientResponseMapper")
    val httpClientTelemetryFactory = ClassName("io.koraframework.http.client.common.telemetry", "HttpClientTelemetryFactory")
    val stringParameterConverter = ClassName("io.koraframework.http.client.common.writer", "StringParameterConverter")
    val httpRoute = ClassName("io.koraframework.http.common.annotation", "HttpRoute")
    val httpClientUnknownException = ClassName("io.koraframework.http.client.common", "HttpClientUnknownException")
    val httpClientOperationConfig = ClassName("io.koraframework.http.client.common.declarative", "HttpClientOperationConfig")
    val declarativeHttpClientConfig = ClassName("io.koraframework.http.client.common.declarative", "DeclarativeHttpClientConfig")
    val telemetryHttpClientConfig = ClassName("io.koraframework.http.client.common.telemetry", "HttpClientTelemetryConfig")
    val interceptWithClassName = ClassName("io.koraframework.http.common.annotation", "InterceptWith")
    val interceptWithContainerClassName = ClassName("io.koraframework.http.common.annotation", "InterceptWith", "InterceptWithContainer")
    val httpClientEncoderUtils = ClassName("io.koraframework.http.client.common.request", "EncoderUtils")
    val httpResponseEntity = ClassName("io.koraframework.http.common", "HttpResponseEntity");
    val httpClientResponseEntityMapper = ClassName("io.koraframework.http.client.common.response", "HttpClientResponseEntityMapper");

    val httpBody = ClassName("io.koraframework.http.common.body", "HttpBody")
    val httpHeaders = ClassName("io.koraframework.http.common.header", "HttpHeaders")
    val httpCookie = ClassName("io.koraframework.http.common.cookie", "Cookie")
    val uriQueryBuilder = ClassName("io.koraframework.http.client.common.request", "UriQueryBuilder");

    val header = ClassName("io.koraframework.http.common.annotation", "Header")
    val query = ClassName("io.koraframework.http.common.annotation", "Query")
    val path = ClassName("io.koraframework.http.common.annotation", "Path")
    val cookie = ClassName("io.koraframework.http.common.annotation", "Cookie")
}

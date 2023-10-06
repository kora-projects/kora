package ru.tinkoff.kora.http.client.symbol.processor

import com.squareup.kotlinpoet.ClassName

object HttpClientClassNames {
    val httpClient = ClassName("ru.tinkoff.kora.http.client.common", "HttpClient")
    val httpClientAnnotation = ClassName("ru.tinkoff.kora.http.client.common.annotation", "HttpClient")
    val httpClientException = ClassName("ru.tinkoff.kora.http.client.common", "HttpClientException")
    val httpClientEncoderException = ClassName("ru.tinkoff.kora.http.client.common", "HttpClientEncoderException")
    val httpClientResponseException = ClassName("ru.tinkoff.kora.http.client.common", "HttpClientResponseException")
    val responseCodeMapper = ClassName("ru.tinkoff.kora.http.client.common.annotation", "ResponseCodeMapper")
    val responseCodeMappers = responseCodeMapper.nestedClass("ResponseCodeMappers")
    val httpClientRequestBuilder = ClassName("ru.tinkoff.kora.http.client.common.request", "HttpClientRequestBuilder")
    val httpClientRequestMapper = ClassName("ru.tinkoff.kora.http.client.common.request", "HttpClientRequestMapper")
    val httpClientResponse = ClassName("ru.tinkoff.kora.http.client.common.response", "HttpClientResponse")
    val httpClientResponseMapper = ClassName("ru.tinkoff.kora.http.client.common.response", "HttpClientResponseMapper")
    val httpClientTelemetryFactory = ClassName("ru.tinkoff.kora.http.client.common.telemetry", "HttpClientTelemetryFactory")
    val stringParameterConverter = ClassName("ru.tinkoff.kora.http.client.common.writer", "StringParameterConverter")
    val httpRoute = ClassName("ru.tinkoff.kora.http.common.annotation", "HttpRoute")
    val httpClientUnknownException = ClassName("ru.tinkoff.kora.http.client.common", "HttpClientUnknownException")
    val httpClientOperationConfig = ClassName("ru.tinkoff.kora.http.client.common.declarative", "HttpClientOperationConfig")
    val declarativeHttpClientConfig = ClassName("ru.tinkoff.kora.http.client.common.declarative", "DeclarativeHttpClientConfig")
    val interceptWithClassName = ClassName("ru.tinkoff.kora.http.common.annotation", "InterceptWith")
    val interceptWithContainerClassName = ClassName("ru.tinkoff.kora.http.common.annotation", "InterceptWith", "InterceptWithContainer")

    val header = ClassName("ru.tinkoff.kora.http.common.annotation", "Header")
    val query = ClassName("ru.tinkoff.kora.http.common.annotation", "Query")
    val path = ClassName("ru.tinkoff.kora.http.common.annotation", "Path")

}

package io.koraframework.http.server.symbol.procesor

import com.squareup.kotlinpoet.ClassName

object HttpServerClassNames {

    val httpRoute = ClassName("io.koraframework.http.common.annotation", "HttpRoute")
    val query = ClassName("io.koraframework.http.common.annotation", "Query");
    val header = ClassName("io.koraframework.http.common.annotation", "Header");
    val path = ClassName("io.koraframework.http.common.annotation", "Path");
    val cookie = ClassName("io.koraframework.http.common.annotation", "Cookie");
    val httpResponseEntity = ClassName("io.koraframework.http.common", "HttpResponseEntity");
    val interceptWith = ClassName("io.koraframework.http.common.annotation", "InterceptWith");
    val interceptWithContainer = ClassName("io.koraframework.http.common.annotation", "InterceptWith", "InterceptWithContainer");
    val httpController = ClassName("io.koraframework.http.server.common.annotation", "HttpController");

    val httpServerRequest = ClassName("io.koraframework.http.server.common.request", "HttpServerRequest");
    val stringParameterReader = ClassName("io.koraframework.http.server.common.request", "HttpServerParameterReader");
    val httpServerRequestMapper = ClassName("io.koraframework.http.server.common.request", "HttpServerRequestMapper");
    val httpServerRequestHandler = ClassName("io.koraframework.http.server.common.request", "HttpServerRequestHandler");
    val httpServerRequestHandlerImpl = ClassName("io.koraframework.http.server.common.request", "HttpServerRequestHandlerImpl");

    val httpServerResponse = ClassName("io.koraframework.http.server.common.response", "HttpServerResponse");
    val httpServerResponseMapper = ClassName("io.koraframework.http.server.common.response", "HttpServerResponseMapper");
    val httpServerResponseException = ClassName("io.koraframework.http.server.common.response", "HttpServerResponseException");
    val httpServerResponseEntityMapper = ClassName("io.koraframework.http.server.common.response.mapper", "HttpServerResponseEntityMapper");
}

package io.koraframework.http.server.symbol.procesor

import com.squareup.kotlinpoet.ClassName

object HttpServerClassNames {

    val httpController = ClassName("io.koraframework.http.server.common.annotation", "HttpController");
    val httpRoute = ClassName("io.koraframework.http.common.annotation", "HttpRoute")
    val query = ClassName("io.koraframework.http.common.annotation", "Query");
    val header = ClassName("io.koraframework.http.common.annotation", "Header");
    val path = ClassName("io.koraframework.http.common.annotation", "Path");
    val cookie = ClassName("io.koraframework.http.common.annotation", "Cookie");

    val httpServerResponse = ClassName("io.koraframework.http.server.common", "HttpServerResponse");
    val httpResponseEntity = ClassName("io.koraframework.http.common", "HttpResponseEntity");
    val httpServerResponseEntityMapper = ClassName("io.koraframework.http.server.common.handler", "HttpServerResponseEntityMapper");
    val httpServerResponseException = ClassName("io.koraframework.http.server.common", "HttpServerResponseException");

    val stringParameterReader = ClassName("io.koraframework.http.server.common.handler", "StringParameterReader");

    val httpServerRequestHandler = ClassName("io.koraframework.http.server.common.handler", "HttpServerRequestHandler");
    val httpServerRequestHandlerImpl = ClassName("io.koraframework.http.server.common.handler", "HttpServerRequestHandlerImpl");
    val interceptWith = ClassName("io.koraframework.http.common.annotation", "InterceptWith");
    val interceptWithContainer = ClassName("io.koraframework.http.common.annotation", "InterceptWith", "InterceptWithContainer");
    val httpServerResponseMapper = ClassName("io.koraframework.http.server.common.handler", "HttpServerResponseMapper");
    val httpServerRequestMapper = ClassName("io.koraframework.http.server.common.handler", "HttpServerRequestMapper");
    val httpServerRequest = ClassName("io.koraframework.http.server.common", "HttpServerRequest");

}

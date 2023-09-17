package ru.tinkoff.kora.http.server.symbol.procesor

import com.squareup.kotlinpoet.ClassName

object HttpServerClassNames {

    val httpController = ClassName("ru.tinkoff.kora.http.server.common.annotation", "HttpController");
    val httpRoute = ClassName("ru.tinkoff.kora.http.common.annotation", "HttpRoute")
    val query = ClassName("ru.tinkoff.kora.http.common.annotation", "Query");
    val header = ClassName("ru.tinkoff.kora.http.common.annotation", "Header");
    val path = ClassName("ru.tinkoff.kora.http.common.annotation", "Path");
    val blockingRequestExecutor = ClassName("ru.tinkoff.kora.http.server.common.handler", "BlockingRequestExecutor");

    val httpServerResponse = ClassName("ru.tinkoff.kora.http.server.common", "HttpServerResponse");
    val httpServerResponseEntity = ClassName("ru.tinkoff.kora.http.server.common", "HttpServerResponse");
    val httpServerResponseException = ClassName("ru.tinkoff.kora.http.server.common", "HttpServerResponseException");

    val stringParameterReader = ClassName("ru.tinkoff.kora.http.server.common.handler", "StringParameterReader");

    val httpServerRequestHandler = ClassName("ru.tinkoff.kora.http.server.common.handler", "HttpServerRequestHandler");
    val httpServerRequestHandlerImpl = ClassName("ru.tinkoff.kora.http.server.common.handler", "HttpServerRequestHandlerImpl");
    val interceptWith = ClassName("ru.tinkoff.kora.http.common.annotation", "InterceptWith");
    val interceptWithContainer = ClassName("ru.tinkoff.kora.http.common.annotation", "InterceptWith", "InterceptWithContainer");
    val httpServerResponseMapper = ClassName("ru.tinkoff.kora.http.server.common.handler", "HttpServerResponseMapper");
    val httpServerRequestMapper = ClassName("ru.tinkoff.kora.http.server.common.handler", "HttpServerRequestMapper");
    val httpServerRequest = ClassName("ru.tinkoff.kora.http.server.common", "HttpServerRequest");

}

package ru.tinkoff.kora.http.server.annotation.processor;

import com.squareup.javapoet.ClassName;

public class HttpServerClassNames {
    public static final ClassName httpController = ClassName.get("ru.tinkoff.kora.http.server.common.annotation", "HttpController");
    public static final ClassName query = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Query");
    public static final ClassName header = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Header");
    public static final ClassName path = ClassName.get("ru.tinkoff.kora.http.common.annotation", "Path");

    public static final ClassName httpServerResponse = ClassName.get("ru.tinkoff.kora.http.server.common", "HttpServerResponse");
    public static final ClassName httpServerResponseException = ClassName.get("ru.tinkoff.kora.http.server.common", "HttpServerResponseException");

    public static final ClassName stringParameterReader = ClassName.get("ru.tinkoff.kora.http.server.common.handler", "StringParameterReader");

    public static final ClassName httpServerRequestHandlerImpl = ClassName.get("ru.tinkoff.kora.http.server.common.handler", "HttpServerRequestHandlerImpl");
    public static final ClassName httpServerRequestHandler = ClassName.get("ru.tinkoff.kora.http.server.common.handler", "HttpServerRequestHandler");
    public static final ClassName interceptWithClassName = ClassName.get("ru.tinkoff.kora.http.common.annotation", "InterceptWith");
    public static final ClassName interceptWithContainerClassName = ClassName.get("ru.tinkoff.kora.http.common.annotation", "InterceptWith", "InterceptWithContainer");
    public static final ClassName httpServerResponseMapper = ClassName.get("ru.tinkoff.kora.http.server.common.handler", "HttpServerResponseMapper");
    public static final ClassName httpServerRequestMapper = ClassName.get("ru.tinkoff.kora.http.server.common.handler", "HttpServerRequestMapper");
    public static final ClassName httpServerRequest = ClassName.get("ru.tinkoff.kora.http.server.common", "HttpServerRequest");
    public static final ClassName httpRoute = ClassName.get("ru.tinkoff.kora.http.common.annotation", "HttpRoute");
    public static final ClassName blockingRequestExecutor = ClassName.get("ru.tinkoff.kora.http.server.common.handler", "BlockingRequestExecutor");
    public static final ClassName requestHandlerUtils = ClassName.get("ru.tinkoff.kora.http.server.common.handler", "RequestHandlerUtils");
}

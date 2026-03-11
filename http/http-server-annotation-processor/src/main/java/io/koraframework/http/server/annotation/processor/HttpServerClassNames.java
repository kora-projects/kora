package io.koraframework.http.server.annotation.processor;

import com.palantir.javapoet.ClassName;

public class HttpServerClassNames {
    public static final ClassName httpController = ClassName.get("io.koraframework.http.server.common.annotation", "HttpController");
    public static final ClassName query = ClassName.get("io.koraframework.http.common.annotation", "Query");
    public static final ClassName header = ClassName.get("io.koraframework.http.common.annotation", "Header");
    public static final ClassName path = ClassName.get("io.koraframework.http.common.annotation", "Path");
    public static final ClassName cookie = ClassName.get("io.koraframework.http.common.annotation", "Cookie");

    public static final ClassName httpServerResponse = ClassName.get("io.koraframework.http.server.common", "HttpServerResponse");
    public static final ClassName httpResponseEntity = ClassName.get("io.koraframework.http.common", "HttpResponseEntity");
    public static final ClassName httpServerResponseEntityMapper = ClassName.get("io.koraframework.http.server.common.handler", "HttpServerResponseEntityMapper");
    public static final ClassName httpServerResponseException = ClassName.get("io.koraframework.http.server.common", "HttpServerResponseException");

    public static final ClassName stringParameterReader = ClassName.get("io.koraframework.http.server.common.handler", "StringParameterReader");

    public static final ClassName httpServerRequestHandlerImpl = ClassName.get("io.koraframework.http.server.common.handler", "HttpServerRequestHandlerImpl");
    public static final ClassName httpServerRequestHandler = ClassName.get("io.koraframework.http.server.common.handler", "HttpServerRequestHandler");
    public static final ClassName interceptWithClassName = ClassName.get("io.koraframework.http.common.annotation", "InterceptWith");
    public static final ClassName interceptWithContainerClassName = ClassName.get("io.koraframework.http.common.annotation", "InterceptWith", "InterceptWithContainer");
    public static final ClassName httpServerResponseMapper = ClassName.get("io.koraframework.http.server.common.handler", "HttpServerResponseMapper");
    public static final ClassName httpServerRequestMapper = ClassName.get("io.koraframework.http.server.common.handler", "HttpServerRequestMapper");
    public static final ClassName httpServerRequest = ClassName.get("io.koraframework.http.server.common", "HttpServerRequest");
    public static final ClassName httpRoute = ClassName.get("io.koraframework.http.common.annotation", "HttpRoute");
    public static final ClassName requestHandlerUtils = ClassName.get("io.koraframework.http.server.common.handler", "RequestHandlerUtils");
}

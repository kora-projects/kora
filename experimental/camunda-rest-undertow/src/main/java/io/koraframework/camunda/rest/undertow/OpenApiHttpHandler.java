package io.koraframework.camunda.rest.undertow;

import io.koraframework.camunda.rest.CamundaRestConfig;
import io.koraframework.http.common.HttpMethod;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.cookie.Cookie;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.request.HttpServerRequestHandler;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.openapi.management.OpenApiManagementConfig;
import io.koraframework.openapi.management.ScalarHttpServerHandler;
import io.koraframework.openapi.management.SwaggerUIHttpServerHandler;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import org.jspecify.annotations.Nullable;
import org.xnio.IoUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class OpenApiHttpHandler implements HttpHandler {

    private final UndertowPathMatcher pathMatcher;
    private final CamundaRestConfig restConfig;

    private final CamundaOpenApiHttpServerHandler openApiHandler;
    private final SwaggerUIHttpServerHandler swaggerUIHandler;
    private final ScalarHttpServerHandler scalarHandler;

    OpenApiHttpHandler(CamundaRestConfig restConfig) {
        this.restConfig = restConfig;

        final List<UndertowPathMatcher.HttpMethodPath> openapiMethods = new ArrayList<>();
        var openapi = restConfig.openapi();
        if (openapi.files().size() == 1) {
            openapiMethods.add(new UndertowPathMatcher.HttpMethodPath(HttpMethod.GET, openapi.path()));
        } else {
            openapiMethods.add(new UndertowPathMatcher.HttpMethodPath(HttpMethod.GET, openapi.path() + "/{file}"));
        }
        openapiMethods.add(new UndertowPathMatcher.HttpMethodPath(HttpMethod.GET, openapi.scalar().path()));
        openapiMethods.add(new UndertowPathMatcher.HttpMethodPath(HttpMethod.GET, openapi.swaggerui().path()));
        this.pathMatcher = new UndertowPathMatcher(openapiMethods);

        this.openApiHandler = new CamundaOpenApiHttpServerHandler(openapi.files(), openapi.cache(), restConfig.path(), restConfig.port());
        this.swaggerUIHandler = new SwaggerUIHttpServerHandler(openapi.path(), new OpenApiManagementConfig.SwaggerUIConfig() {
            @Override
            public boolean enabled() {
                return openapi.swaggerui().enabled();
            }

            @Override
            public String path() {
                return openapi.swaggerui().path();
            }

            @Override
            public boolean withCredentials() {
                return openapi.swaggerui().withCredentials();
            }

            @Override
            public OpenApiManagementConfig.CacheMode cache() {
                return cacheMode(openapi.swaggerui().cache());
            }

            @Override
            public Map<String, String> options() {
                return openapi.swaggerui().options();
            }
        }, openapi.files());
        this.scalarHandler = new ScalarHttpServerHandler(openapi.path(), new OpenApiManagementConfig.ScalarConfig() {
            @Override
            public boolean enabled() {
                return openapi.scalar().enabled();
            }

            @Override
            public String path() {
                return openapi.scalar().path();
            }

            @Override
            public OpenApiManagementConfig.CacheMode cache() {
                return cacheMode(openapi.scalar().cache());
            }
        }, openapi.files());
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        var requestPath = exchange.getRequestPath();
        var match = pathMatcher.getMatch(exchange.getRequestMethod().toString(), requestPath);
        if (match == null) {
            exchange.setStatusCode(404);
            exchange.endExchange();
            return;
        }
        var fakeRequest = getFakeRequest(exchange, match);
        var openapi = restConfig.openapi();
        if (openapi.enabled() && requestPath.startsWith(openapi.path())) {
            executeHandler(exchange, openApiHandler, fakeRequest);
        } else if (openapi.swaggerui().enabled() && requestPath.startsWith(openapi.swaggerui().path())) {
            executeHandler(exchange, swaggerUIHandler, fakeRequest);
        } else if (openapi.scalar().enabled() && requestPath.startsWith(openapi.scalar().path())) {
            executeHandler(exchange, scalarHandler, fakeRequest);
        } else {
            exchange.setStatusCode(404);
            exchange.endExchange();
        }
    }

    private HttpServerRequest getFakeRequest(HttpServerExchange exchange, UndertowPathMatcher.Match match) {
        return new HttpServerRequest() {
            @Override
            public String scheme() {
                return "http";
            }

            @Override
            public String host() {
                return "localhost";
            }

            @Override
            public String method() {
                return "";
            }

            @Override
            public String path() {
                return "";
            }

            @Override
            public String pathTemplate() {
                return "";
            }

            @Override
            public HttpHeaders headers() {
                var acceptEncoding = exchange.getRequestHeaders().getFirst(Headers.ACCEPT_ENCODING);
                return acceptEncoding == null
                    ? HttpHeaders.empty()
                    : HttpHeaders.of("accept-encoding", acceptEncoding);
            }

            @Override
            public List<Cookie> cookies() {
                return List.of();
            }

            @Override
            public Map<String, List<String>> queryParams() {
                return Map.of();
            }

            @Override
            public Map<String, String> pathParams() {
                return restConfig.openapi().files().size() == 1
                    ? Map.of()
                    : Map.of("file", match.pathParameters().get("file"));
            }

            @Override
            public HttpBodyInput body() {
                return null;
            }

            @Override
            public long requestStartTimeInNanos() {
                return 0;
            }
        };
    }

    private static OpenApiManagementConfig.CacheMode cacheMode(CamundaRestConfig.CamundaOpenApiConfig.CacheMode cacheMode) {
        return switch (cacheMode) {
            case NONE -> OpenApiManagementConfig.CacheMode.NONE;
            case GZIP -> OpenApiManagementConfig.CacheMode.GZIP;
            case FULL -> OpenApiManagementConfig.CacheMode.FULL;
        };
    }

    private void executeHandler(HttpServerExchange exchange, HttpServerRequestHandler.HandlerFunction handler, HttpServerRequest request) {
        final HttpServerResponse response;
        try {
            response = handler.apply(request);
        } catch (Throwable e) {
            sendException(exchange, e);
            return;
        }
        sendResponse(exchange, response);

    }

    private void sendResponse(HttpServerExchange exchange,
                              HttpServerResponse httpResponse) {
        var headers = httpResponse.headers();
        exchange.setStatusCode(httpResponse.code());
        setHeaders(exchange.getResponseHeaders(), headers, null);
        var body = httpResponse.body();
        if (body == null) {
            exchange.endExchange();
            return;
        }

        var contentType = body.contentType();
        if (contentType != null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
        }

        var full = body.getFullContentIfAvailable();
        if (full != null) {
            this.sendBody(exchange, httpResponse, full);
        } else {
            throw new IllegalStateException("Shouldn't happen");
        }
    }

    private void setHeaders(HeaderMap responseHeaders, HttpHeaders headers, @Nullable String contentType) {
        for (var header : headers) {
            var key = header.getKey();
            if (key.equals("server")) {
                continue;
            }
            if (key.equals("content-type") && contentType != null) {
                continue;
            }
            if (key.equals("content-length")) {
                continue;
            }
            if (key.equals("transfer-encoding")) {
                continue;
            }
            responseHeaders.addAll(HttpString.tryFromString(key), header.getValue());
        }
    }

    private void sendBody(HttpServerExchange exchange,
                          HttpServerResponse httpResponse,
                          @Nullable ByteBuffer body) {
        var headers = httpResponse.headers();
        if (body == null || body.remaining() == 0) {
            exchange.setResponseContentLength(0);
            exchange.endExchange();
        } else {
            exchange.setResponseContentLength(body.remaining());
            // io.undertow.io.DefaultIoCallback
            exchange.getResponseSender().send(body, new IoCallback() {
                @Override
                public void onComplete(HttpServerExchange exchange, Sender sender) {
                    sender.close(new IoCallback() {
                        @Override
                        public void onComplete(HttpServerExchange exchange, Sender sender) {
                            exchange.endExchange();
                        }

                        @Override
                        public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                            exchange.endExchange();
                        }
                    });
                }

                @Override
                public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                    try {
                        exchange.endExchange();
                    } finally {
                        IoUtils.safeClose(exchange.getConnection());
                    }
                }
            });
        }
    }

    private void sendException(HttpServerExchange exchange,
                               Throwable error) {
        if (!(error instanceof HttpServerResponse rs)) {
            exchange.setStatusCode(500);
            exchange.getResponseSender().send(Objects.requireNonNullElse(error.getMessage(), "Unknown error"), StandardCharsets.UTF_8, new IoCallback() {
                @Override
                public void onComplete(HttpServerExchange exchange, Sender sender) {
                    IoCallback.END_EXCHANGE.onComplete(exchange, sender);
                }

                @Override
                public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                    error.addSuppressed(exception);
                    IoCallback.END_EXCHANGE.onException(exchange, sender, exception);
                }
            });
            exchange.endExchange();
        } else {
            sendResponse(exchange, rs);
        }
    }
}

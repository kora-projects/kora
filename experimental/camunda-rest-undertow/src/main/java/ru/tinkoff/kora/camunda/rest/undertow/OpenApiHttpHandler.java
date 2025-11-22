package ru.tinkoff.kora.camunda.rest.undertow;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.AttachmentKey;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import jakarta.annotation.Nullable;
import org.xnio.IoUtils;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;
import ru.tinkoff.kora.http.common.HttpMethod;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.cookie.Cookie;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestHandler;
import ru.tinkoff.kora.http.server.undertow.UndertowHttpServer;
import ru.tinkoff.kora.openapi.management.OpenApiHttpServerHandler;
import ru.tinkoff.kora.openapi.management.RapidocHttpServerHandler;
import ru.tinkoff.kora.openapi.management.SwaggerUIHttpServerHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;

final class OpenApiHttpHandler implements HttpHandler {

    private final UndertowPathMatcher pathMatcher;
    private final CamundaRestConfig restConfig;

    private final OpenApiHttpServerHandler openApiHandler;
    private final SwaggerUIHttpServerHandler swaggerUIHandler;
    private final RapidocHttpServerHandler rapidocHandler;
    private final AttachmentKey<ExecutorService> executorServiceAttachmentKey = AttachmentKey.create(ExecutorService.class);

    OpenApiHttpHandler(CamundaRestConfig restConfig) {
        this.restConfig = restConfig;

        final List<UndertowPathMatcher.HttpMethodPath> openapiMethods = new ArrayList<>();
        var openapi = restConfig.openapi();
        if (openapi.file().size() == 1) {
            openapiMethods.add(new UndertowPathMatcher.HttpMethodPath(HttpMethod.GET, openapi.endpoint()));
        } else {
            openapiMethods.add(new UndertowPathMatcher.HttpMethodPath(HttpMethod.GET, openapi.endpoint() + "/{file}"));
        }
        openapiMethods.add(new UndertowPathMatcher.HttpMethodPath(HttpMethod.GET, openapi.rapidoc().endpoint()));
        openapiMethods.add(new UndertowPathMatcher.HttpMethodPath(HttpMethod.GET, openapi.swaggerui().endpoint()));
        this.pathMatcher = new UndertowPathMatcher(openapiMethods);

        this.openApiHandler = new OpenApiHttpServerHandler(openapi.file(), f -> {
            if ("/engine-rest".equals(restConfig.path())) {
                String fileAsStr = new String(f, StandardCharsets.UTF_8);
                return fileAsStr
                    .replace("8080", String.valueOf(restConfig.port()))
                    .getBytes(StandardCharsets.UTF_8);
            } else {
                String fileAsStr = new String(f, StandardCharsets.UTF_8);
                String newEnginePath = restConfig.path().startsWith("/")
                    ? restConfig.path().substring(1)
                    : restConfig.path();

                return fileAsStr
                    .replace("engine-rest", newEnginePath)
                    .replace("8080", String.valueOf(restConfig.port()))
                    .getBytes(StandardCharsets.UTF_8);
            }
        });
        this.swaggerUIHandler = new SwaggerUIHttpServerHandler(openapi.endpoint(), openapi.swaggerui().endpoint(), openapi.file());
        this.rapidocHandler = new RapidocHttpServerHandler(openapi.endpoint(), openapi.rapidoc().endpoint(), openapi.file());
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
        var executor = UndertowHttpServer.getOrCreateExecutor(exchange, executorServiceAttachmentKey, "undertow-kora-camunda");
        exchange.dispatch(executor, exchange1 -> {
            var fakeRequest = getFakeRequest(match);
            var openapi = restConfig.openapi();
            if (openapi.enabled() && requestPath.startsWith(openapi.endpoint())) {
                executeHandler(exchange1, openApiHandler, fakeRequest);
            } else if (openapi.swaggerui().enabled() && requestPath.startsWith(openapi.swaggerui().endpoint())) {
                executeHandler(exchange1, swaggerUIHandler, fakeRequest);
            } else if (openapi.rapidoc().enabled() && requestPath.startsWith(openapi.rapidoc().endpoint())) {
                executeHandler(exchange1, rapidocHandler, fakeRequest);
            } else {
                exchange.setStatusCode(404);
                exchange.endExchange();
            }
        });
    }

    private HttpServerRequest getFakeRequest(UndertowPathMatcher.Match match) {
        return new HttpServerRequest() {
            @Override
            public String method() {
                return "";
            }

            @Override
            public String path() {
                return "";
            }

            @Override
            public String route() {
                return "";
            }

            @Override
            public HttpHeaders headers() {
                return null;
            }

            @Override
            public List<Cookie> cookies() {
                return List.of();
            }

            @Override
            public Map<String, ? extends Collection<String>> queryParams() {
                return Map.of();
            }

            @Override
            public Map<String, String> pathParams() {
                return restConfig.openapi().file().size() == 1
                    ? Map.of()
                    : Map.of("file", match.pathParameters().get("file"));
            }

            @Override
            public HttpBodyInput body() {
                return null;
            }
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

package ru.tinkoff.kora.http.server.undertow;

import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnio.IoUtils;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.router.PublicApiResponse;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracer;
import ru.tinkoff.kora.http.server.undertow.request.UndertowPublicApiRequest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class UndertowExchangeProcessor implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

    private final PublicApiHandler publicApiHandler;
    private final Context context;
    @Nullable
    private final HttpServerTracer tracer;

    public UndertowExchangeProcessor(PublicApiHandler publicApiHandler, Context context, @Nullable HttpServerTracer tracer) {
        this.publicApiHandler = publicApiHandler;
        this.context = context;
        this.tracer = tracer;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        var context = this.context;
        UndertowContext.set(context, exchange);
        context.inject();
        try {
            exchange.startBlocking();
            var request = new UndertowPublicApiRequest(exchange);
            var response = this.publicApiHandler.process(context, request);
            var error = response.error();
            try {
                var httpResponse = response.response();
                if (httpResponse != null) {
                    this.sendResponse(exchange, response, httpResponse, null);
                    return;
                }
                if (error == null) {
                    this.sendResponse(exchange, response, HttpServerResponse.of(500), new IllegalStateException("Public api handler should return either response or error"));
                    return;
                }
            } catch (Throwable e) {
                this.sendException(exchange, response, e);
                return;
            }
            this.sendException(exchange, response, response.error());
        } catch (Throwable exception) {
            log.warn("Error dropped", exception);
            exchange.setStatusCode(500);
            exchange.getResponseSender().send(StandardCharsets.UTF_8.encode(Objects.requireNonNullElse(exception.getMessage(), "no message")));
        } finally {
            UndertowContext.clear(context);
            exchange.endExchange();
        }
    }

    private void sendResponse(HttpServerExchange exchange, PublicApiResponse response, HttpServerResponse httpResponse, @Nullable Throwable error) {
        var headers = httpResponse.headers();
        exchange.setStatusCode(httpResponse.code());
        var tracer = this.tracer;
        if (tracer != null) tracer.inject(
            this.context,
            exchange.getResponseHeaders(),
            (carrier, key, value) -> carrier.add(HttpString.tryFromString(key), value)
        );

        exchange.getResponseHeaders().put(Headers.SERVER, "kora/undertow");
        var body = httpResponse.body();
        if (body == null) {
            this.setHeaders(exchange.getResponseHeaders(), headers, null);
            exchange.addExchangeCompleteListener((e, nextListener) -> {
                response.closeSendResponseSuccess(e.getStatusCode(), httpResponse.headers(), error);
                nextListener.proceed();
            });
            exchange.endExchange();
            return;
        } else {
            this.setHeaders(exchange.getResponseHeaders(), headers, body.contentType());
        }

        var contentType = body.contentType();
        if (contentType != null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
        }
        var full = body.getFullContentIfAvailable();
        if (full != null) {
            this.sendFullBody(exchange, response, httpResponse, full, error);
            return;
        }

        var contentLength = body.contentLength();
        if (contentLength >= 0) {
            exchange.setResponseContentLength(contentLength);
        }
        try (var os = exchange.getOutputStream()) {
            try {
                body.write(os);
            } catch (IOException e) {
                if (!exchange.isResponseStarted()) {
                    response.closeBodyError(500, e);
                    exchange.setStatusCode(500);
                    exchange.getResponseHeaders().remove(Headers.CONTENT_LENGTH);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                    exchange.getResponseSender().send(Objects.requireNonNullElse(e.getMessage(), ""));
                    exchange.endExchange();
                } else {
                    response.closeConnectionError(exchange.getStatusCode(), e);
                }
                return;
            } catch (Exception e) {
                if (!exchange.isResponseStarted()) {
                    response.closeBodyError(500, e);
                    exchange.setStatusCode(500);
                    exchange.getResponseHeaders().remove(Headers.CONTENT_LENGTH);
                    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                    exchange.getResponseSender().send(Objects.requireNonNullElse(e.getMessage(), ""));
                    exchange.endExchange();
                } else {
                    response.closeBodyError(exchange.getStatusCode(), e);
                }
                return;
            }
        } catch (IOException e) {
            response.closeConnectionError(exchange.getStatusCode(), e);
            return;
        }
        if (exchange.isComplete()) {
            response.closeSendResponseSuccess(exchange.getStatusCode(), httpResponse.headers(), error);
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


    private void sendFullBody(HttpServerExchange exchange, PublicApiResponse response, HttpServerResponse httpResponse, @Nullable ByteBuffer body, @Nullable Throwable error) {
        var headers = httpResponse.headers();
        if (body == null || body.remaining() == 0) {
            exchange.setResponseContentLength(0);
            exchange.addExchangeCompleteListener((e, nextListener) -> {
                response.closeSendResponseSuccess(e.getStatusCode(), headers, error);
                nextListener.proceed();
            });
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
                            if (exchange.isComplete()) {
                                response.closeSendResponseSuccess(exchange.getStatusCode(), headers, error);
                            } else {
                                exchange.addExchangeCompleteListener((e, nextListener) -> {
                                    response.closeSendResponseSuccess(e.getStatusCode(), headers, error);
                                    nextListener.proceed();
                                });
                                exchange.endExchange();
                            }
                        }

                        @Override
                        public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                            try {
                                exchange.endExchange();
                            } finally {
                                response.closeConnectionError(exchange.getStatusCode(), error == null ? error : exception);
                            }
                        }
                    });
                }

                @Override
                public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                    try {
                        exchange.endExchange();
                    } finally {
                        IoUtils.safeClose(exchange.getConnection());
                        response.closeConnectionError(exchange.getStatusCode(), error == null ? error : exception);
                    }
                }
            });
        }
    }

    private void sendException(HttpServerExchange exchange, PublicApiResponse response, Throwable error) {
        if (!(error instanceof HttpServerResponse rs)) {
            exchange.setStatusCode(500);
            exchange.getResponseSender().send(Objects.requireNonNullElse(error.getMessage(), "Unknown error"), StandardCharsets.UTF_8, new IoCallback() {
                @Override
                public void onComplete(HttpServerExchange exchange, Sender sender) {
                    response.closeSendResponseSuccess(500, null, error);
                    IoCallback.END_EXCHANGE.onComplete(exchange, sender);
                }

                @Override
                public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                    error.addSuppressed(exception);
                    response.closeConnectionError(500, error);
                    IoCallback.END_EXCHANGE.onException(exchange, sender, exception);
                }
            });
            return;
        }
        exchange.setStatusCode(rs.code());
        var body = rs.body();
        if (body == null) {
            this.setHeaders(exchange.getRequestHeaders(), rs.headers(), null);
            exchange.addExchangeCompleteListener((exchange1, nextListener) -> {
                response.closeSendResponseSuccess(exchange1.getStatusCode(), rs.headers(), error);
                nextListener.proceed();
            });
            exchange.setResponseContentLength(0);
            exchange.endExchange();
            return;
        } else {
            this.setHeaders(exchange.getResponseHeaders(), rs.headers(), body.contentType());
        }

        var contentType = body.contentType();
        if (contentType != null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, body.contentType());
        }
        var full = body.getFullContentIfAvailable();
        if (full != null) {
            sendFullBody(exchange, response, rs, full, error);
            return;
        }
        try (var os = exchange.getOutputStream()) {
            body.write(os);
        } catch (IOException e) {
            if (!exchange.isResponseStarted()) {
                exchange.setStatusCode(500);
            } else {
                try {
                    exchange.getConnection().close();
                } catch (IOException ex) {
                    e.addSuppressed(ex);
                }
            }
            response.closeBodyError(exchange.getStatusCode(), e);
            exchange.endExchange();
            return;
        }
        response.closeSendResponseSuccess(exchange.getStatusCode(), rs.headers(), error);
    }
}

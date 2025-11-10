package ru.tinkoff.kora.http.server.undertow;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.undertow.UndertowMessages;
import io.undertow.io.BufferWritableOutputStream;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.body.HttpBody;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerObservation;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetry;
import ru.tinkoff.kora.http.server.undertow.request.UndertowPublicApiRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;

public class UndertowExchangeProcessor implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

    private final HttpServerTelemetry telemetry;
    private final UndertowContext context;
    private final PublicApiHandler publicApiHandler;

    public UndertowExchangeProcessor(HttpServerTelemetry telemetry, UndertowContext context, PublicApiHandler publicApiHandler) {
        this.telemetry = telemetry;
        this.context = context;
        this.publicApiHandler = publicApiHandler;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        var rootCtx = W3CTraceContextPropagator.getInstance().extract(Context.root(), exchange.getRequestHeaders(), HttpServerExchangeMapGetter.INSTANCE);
        ScopedValue
            .where(UndertowContext.VALUE, this.context)
            .where(ru.tinkoff.kora.logging.common.MDC.VALUE, new ru.tinkoff.kora.logging.common.MDC())
            .where(OpentelemetryContext.VALUE, rootCtx)
            .run(() -> {
                MDC.clear();
                try {
                    exchange.startBlocking();
                    var request = new UndertowPublicApiRequest(exchange);
                    var invocation = this.publicApiHandler.route(request);
                    var observation = this.telemetry.observe(request, invocation.request);
                    var ctx = rootCtx.with(observation.span());
                    W3CTraceContextPropagator.getInstance().inject(
                        ctx,
                        exchange.getResponseHeaders(),
                        HttpServerExchangeMapGetter.INSTANCE
                    );
                    exchange.addExchangeCompleteListener((e, nextListener) -> {
                        observation.end();
                        nextListener.proceed();
                    });
                    ScopedValue
                        .where(OpentelemetryContext.VALUE, ctx)
                        .where(Observation.VALUE, observation)
                        .run(() -> {
                            HttpServerResponse response;
                            try {
                                var httpServerRequest = observation.observeRequest(invocation.request);
                                response = invocation.proceed(httpServerRequest);
                            } catch (Throwable e) {
                                observation.observeError(e);
                                if (e instanceof HttpServerResponse rs) {
                                    this.sendResponse(observation, exchange, rs);
                                } else {
                                    this.sendResponse(observation, exchange, HttpServerResponse.of(500, HttpBody.plaintext(Objects.requireNonNullElse(e.getMessage(), "Unknown error"))));
                                }
                                return;
                            }
                            this.sendResponse(observation, exchange, response);
                        });
                } catch (Throwable exception) {
                    exchange.setStatusCode(500);
                    try {
                        exchange.getResponseSender().send(StandardCharsets.UTF_8.encode(Objects.requireNonNullElse(exception.getMessage(), "Unknown error")));
                        exchange.getConnection().close();
                    } catch (Exception e) {
                        exception.addSuppressed(e);
                    }
                    log.warn("Error dropped", exception);
                } finally {
                    exchange.endExchange();
                }
            });
    }


    private void sendResponse(HttpServerObservation observation, HttpServerExchange exchange, HttpServerResponse httpResponse) {
        httpResponse = observation.observeResponse(httpResponse);
        var headers = httpResponse.headers();
        exchange.setStatusCode(httpResponse.code());
        exchange.getResponseHeaders().put(Headers.SERVER, "kora/undertow");
        var body = httpResponse.body();
        if (body == null) {
            this.setHeaders(exchange.getResponseHeaders(), headers, null);
            return;
        }
        try (body) {
            var contentType = body.contentType();
            this.setHeaders(exchange.getResponseHeaders(), headers, contentType);
            if (contentType != null) {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, contentType);
            }
            var contentLength = body.contentLength();
            if (contentLength >= 0) {
                exchange.setResponseContentLength(contentLength);
            }
            try (var os = exchange.getOutputStream()) {
                try {
                    var full = body.getFullContentIfAvailable();
                    if (full != null) {
                        this.writeBuffer(exchange, os, full);
                        return;
                    }
                    body.write(os);
                } catch (Throwable t) {
                    if (!exchange.isResponseStarted()) {
                        observation.observeResponse(HttpServerResponse.of(500, HttpBody.plaintext(Objects.requireNonNullElse(t.getMessage(), "Unknown error"))));
                        exchange.setStatusCode(500);
                        exchange.getResponseHeaders().remove(Headers.CONTENT_LENGTH);
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                        exchange.getResponseSender().send(Objects.requireNonNullElse(t.getMessage(), "Unknown error"));
                    } else {
                        observation.observeResultCode(HttpResultCode.CONNECTION_ERROR);
                    }
                    throw t;
                }
            }
        } catch (Throwable e) {
            observation.observeError(e);
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

    private void writeBuffer(HttpServerExchange exchange, OutputStream outputStream, ByteBuffer buffer) throws IOException {
        if (outputStream instanceof BufferWritableOutputStream bufferWritableOutputStream) {
            //fast path, if the stream can take a buffer directly just write to it
            bufferWritableOutputStream.write(buffer);
            return;
        }
        if (buffer.hasArray()) {
            outputStream.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
            return;
        }
        try (var pooled = exchange.getConnection().getByteBufferPool().getArrayBackedPool().allocate()) {
            if (pooled == null) {
                throw UndertowMessages.MESSAGES.failedToAllocateResource();
            }
            while (buffer.hasRemaining()) {
                var toRead = Math.min(buffer.remaining(), pooled.getBuffer().remaining());
                buffer.get(pooled.getBuffer().array(), pooled.getBuffer().arrayOffset(), toRead);
                outputStream.write(pooled.getBuffer().array(), pooled.getBuffer().arrayOffset(), toRead);
            }
        }
    }

    private static class HttpServerExchangeMapGetter implements TextMapGetter<HeaderMap>, TextMapSetter<HeaderMap> {
        private static final HttpServerExchangeMapGetter INSTANCE = new HttpServerExchangeMapGetter();

        @Override
        public Iterable<String> keys(HeaderMap header) {
            return () -> new Iterator<>() {
                final Iterator<HeaderValues> i = header.iterator();

                @Override
                public boolean hasNext() {
                    return i.hasNext();
                }

                @Override
                public String next() {
                    return i.next().getHeaderName().toString();
                }
            };
        }

        @Override
        public String get(HeaderMap headers, String key) {
            return headers.getFirst(key);
        }

        @Override
        public void set(HeaderMap headers, String key, String value) {
            headers.add(HttpString.tryFromString(key), value);
        }
    }

}

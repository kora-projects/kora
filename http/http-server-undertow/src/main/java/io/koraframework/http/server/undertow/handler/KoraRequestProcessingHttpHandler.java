package io.koraframework.http.server.undertow.handler;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.router.HttpServerRouter;
import io.koraframework.http.server.common.telemetry.HttpServerObservation;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.impl.NoopHttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.impl.NoopHttpServerObservation;
import io.koraframework.http.server.undertow.UndertowContext;
import io.koraframework.http.server.undertow.request.UndertowUnroutedHttpRequest;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.undertow.UndertowMessages;
import io.undertow.io.AsyncSenderImpl;
import io.undertow.io.BufferWritableOutputStream;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;

public final class KoraRequestProcessingHttpHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(KoraRequestProcessingHttpHandler.class);

    private final HttpServerTelemetry telemetry;
    private final HttpServerRouter httpServerRouter;
    private final boolean telemetryEnabled;

    public KoraRequestProcessingHttpHandler(HttpServerTelemetry telemetry, HttpServerRouter httpServerRouter) {
        this.telemetry = telemetry;
        this.httpServerRouter = httpServerRouter;
        this.telemetryEnabled = !(telemetry instanceof NoopHttpServerTelemetry);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        ProcessedResponse processedResponse;
        try {
            processedResponse = this.processRequest(exchange);
        } catch (Throwable e) {
            logger.warn("HTTP request processing failed", e);
            processedResponse = errorResponse(NoopHttpServerObservation.INSTANCE, Context.root(), null, e);
        }
        var response = processedResponse;
        exchange.dispatch(exchange.getIoThread(), () -> this.sendResponse(exchange, response));
    }

    private ProcessedResponse processRequest(HttpServerExchange exchange) {
        var rootCtx = this.telemetryEnabled
            ? W3CTraceContextPropagator.getInstance().extract(Context.root(), exchange.getRequestHeaders(), HttpServerExchangeMapGetter.INSTANCE)
            : Context.root();
        return ScopedValue
            .where(UndertowContext.VALUE, new UndertowContext(exchange))
            .where(io.koraframework.logging.common.MDC.VALUE, new io.koraframework.logging.common.MDC())
            .where(OpentelemetryContext.VALUE, rootCtx)
            .call(() -> {
                MDC.clear();
                try {
                    var request = new UndertowUnroutedHttpRequest(exchange);
                    var invocation = this.httpServerRouter.route(request);
                    var observation = this.telemetry.observe(invocation.routedRequest());
                    var ctx = rootCtx.with(observation.span());
                    return ScopedValue
                        .where(OpentelemetryContext.VALUE, ctx)
                        .where(Observation.VALUE, observation)
                        .call(() -> {
                            HttpServerResponse response;
                            try {
                                var httpServerRequest = observation.observeRequest(invocation.routedRequest());
                                response = invocation.proceed(httpServerRequest);
                            } catch (Throwable e) {
                                observation.observeError(e);
                                if (e instanceof HttpServerResponse rs) {
                                    response = rs;
                                } else {
                                    return errorResponse(observation, ctx, null, e);
                                }
                            }
                            return this.materializeResponse(observation, ctx, response, exchange.getRequestMethod().equals(Methods.HEAD));
                        });
                } finally {
                    MDC.clear();
                }
            });
    }

    private ProcessedResponse materializeResponse(HttpServerObservation observation, Context context, HttpServerResponse response, boolean headRequest) {
        response = observation.observeResponse(response);
        var body = response.body();
        if (body == null) {
            return new ProcessedResponse(observation, context, response.code(), response.headers(), null, null, -1, null, null);
        }
        var declaredLength = body.contentLength();
        if (headRequest) {
            return new ProcessedResponse(observation, context, response.code(), response.headers(), body.contentType(), ByteBuffer.allocate(0), declaredLength, body, null);
        }
        ByteArrayOutputStream output = null;
        try {
            var contentType = body.contentType();
            var content = body.getFullContentIfAvailable();
            if (content == null) {
                output = new ByteArrayOutputStream(declaredLength > 0 && declaredLength <= Integer.MAX_VALUE
                    ? (int) declaredLength
                    : 256);
                body.write(output);
                content = ByteBuffer.wrap(output.toByteArray());
            } else {
                content = content.slice();
            }
            var contentLength = declaredLength >= 0 ? declaredLength : content.remaining();
            return new ProcessedResponse(observation, context, response.code(), response.headers(), contentType, content, contentLength, body, null);
        } catch (Throwable e) {
            observation.observeError(e);
            if (output != null && output.size() > 0) {
                var content = ByteBuffer.wrap(output.toByteArray());
                var contentLength = declaredLength >= 0 ? declaredLength : content.remaining() + 1L;
                return new ProcessedResponse(observation, context, response.code(), response.headers(), body.contentType(), content, contentLength, body, e);
            }
            return errorResponse(observation, context, body, e);
        }
    }

    private void writeBuffer(HttpServerExchange exchange, OutputStream outputStream, ByteBuffer buffer) throws IOException {
        if (outputStream instanceof BufferWritableOutputStream bufferWritableOutputStream) {
            //fast path, if the stream can take a buffer directly just write to it
            bufferWritableOutputStream.write(buffer);
            return;
        }
        if (buffer.hasArray()) {
            outputStream.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
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

    private void writeBufferNew(HttpServerExchange exchange, OutputStream outputStream, ByteBuffer buffer) throws IOException {
        if (outputStream instanceof BufferWritableOutputStream bufferWritableOutputStream) {
            //fast path, if the stream can take a buffer directly just write to it
            bufferWritableOutputStream.write(buffer);
            return;
        }
        if (buffer.hasArray()) {
            outputStream.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
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

    private static ProcessedResponse errorResponse(HttpServerObservation observation, Context context, @Nullable HttpBody bodyToClose, Throwable error) {
        var message = Objects.requireNonNullElse(error.getMessage(), "Unknown error");
        var content = message.getBytes(StandardCharsets.UTF_8);
        var response = observation.observeResponse(HttpServerResponse.of(500, HttpBody.plaintext(message)));
        return new ProcessedResponse(
            observation,
            context,
            response.code(),
            response.headers(),
            "text/plain;charset=utf-8",
            ByteBuffer.wrap(content),
            content.length,
            bodyToClose,
            null
        );
    }

    private void sendResponse(HttpServerExchange exchange, ProcessedResponse response) {
        var observation = response.observation();
        try {
            if (this.telemetryEnabled) {
                W3CTraceContextPropagator.getInstance().inject(
                    response.context(),
                    exchange.getResponseHeaders(),
                    HttpServerExchangeMapGetter.INSTANCE
                );
                exchange.addExchangeCompleteListener((e, nextListener) -> {
                    observation.end();
                    nextListener.proceed();
                });
            }
            exchange.setStatusCode(response.code());
            exchange.getResponseHeaders().put(Headers.SERVER, "Kora");
            this.setHeaders(exchange.getResponseHeaders(), response.headers(), response.contentType());
            if (response.contentType() != null) {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, response.contentType());
            }
            var content = response.content();
            if (content == null) {
                closeBody(observation, response.body());
                exchange.endExchange();
                return;
            }
            exchange.setResponseContentLength(response.contentLength());
            new AsyncSenderImpl(exchange).send(content, new IoCallback() {
                @Override
                public void onComplete(HttpServerExchange exchange, Sender sender) {
                    closeBody(observation, response.body());
                    if (response.sendFailure() != null) {
                        observation.observeResultCode(HttpResultCode.CONNECTION_ERROR);
                        try {
                            exchange.getConnection().close();
                        } catch (IOException closeException) {
                            response.sendFailure().addSuppressed(closeException);
                        }
                        return;
                    }
                    IoCallback.END_EXCHANGE.onComplete(exchange, sender);
                }

                @Override
                public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                    observation.observeError(exception);
                    observation.observeResultCode(HttpResultCode.CONNECTION_ERROR);
                    closeBody(observation, response.body());
                    IoCallback.END_EXCHANGE.onException(exchange, sender, exception);
                }
            });
        } catch (Throwable e) {
            observation.observeError(e);
            observation.observeResultCode(HttpResultCode.CONNECTION_ERROR);
            closeBody(observation, response.body());
            exchange.endExchange();
            try {
                exchange.getConnection().close();
            } catch (IOException closeException) {
                e.addSuppressed(closeException);
            }
            logger.warn("HTTP response send failed", e);
        }
    }

    private static void closeBody(HttpServerObservation observation, @Nullable HttpBody body) {
        if (body == null) {
            return;
        }
        try {
            body.close();
        } catch (IOException e) {
            observation.observeError(e);
        }
    }

    private record ProcessedResponse(HttpServerObservation observation,
                                     Context context,
                                     int code,
                                     HttpHeaders headers,
                                     @Nullable String contentType,
                                     @Nullable ByteBuffer content,
                                     long contentLength,
                                     @Nullable HttpBody body,
                                     @Nullable Throwable sendFailure) {}

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

    public static class HttpServerExchangeMapGetter implements TextMapGetter<HeaderMap>, TextMapSetter<HeaderMap> {
        public static final HttpServerExchangeMapGetter INSTANCE = new HttpServerExchangeMapGetter();

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

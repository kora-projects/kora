package io.koraframework.http.server.undertow.handler;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyOutput;
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
import io.undertow.io.AsyncSenderImpl;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.SameThreadExecutor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KoraRequestProcessingHttpHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(KoraRequestProcessingHttpHandler.class);
    public static final ByteBuffer BODY_EMPTY = ByteBuffer.allocate(0);

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
        if (!response.streamingStarted()) {
            exchange.dispatch(exchange.getIoThread(), () -> this.sendResponse(exchange, response));
        }
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
                            return this.prepareResponse(exchange, observation, ctx, response, exchange.getRequestMethod().equals(Methods.HEAD));
                        });
                } finally {
                    MDC.clear();
                }
            });
    }

    private ProcessedResponse prepareResponse(HttpServerExchange exchange, HttpServerObservation observation, Context context, HttpServerResponse response, boolean headRequest) {
        response = observation.observeResponse(response);
        var body = response.body();
        if (body == null) {
            return new ProcessedResponse(observation, context, response.code(), response.headers(), null, null, -1, null, null, false);
        }

        var declaredLength = body.contentLength();
        if (headRequest) {
            return new ProcessedResponse(observation, context, response.code(), response.headers(), body.contentType(), BODY_EMPTY, declaredLength, body, null, false);
        }

        AdaptiveBodyOutputStream output = null;
        try {
            var contentType = body.contentType();
            var content = body.getFullContentIfAvailable();
            if (content != null) {
                var contentLength = declaredLength >= 0 ? declaredLength : content.remaining();
                return new ProcessedResponse(observation, context, response.code(), response.headers(), contentType, content, contentLength, body, null, false);
            }

            var streamingResponse = new ProcessedResponse(observation, context, response.code(), response.headers(), contentType, null, declaredLength, body, null, true);
            output = new AdaptiveBodyOutputStream(this, exchange, streamingResponse);
            body.write(output);
            if (output.isStreaming()) {
                output.completeStreaming();
                return streamingResponse;
            }

            content = output.fullContent();
            var contentLength = declaredLength >= 0 ? declaredLength : content.remaining();
            return new ProcessedResponse(observation, context, response.code(), response.headers(), contentType, content, contentLength, body, null, false);
        } catch (Throwable e) {
            observation.observeError(e);
            if (output != null && output.isStreaming()) {
                output.failStreaming(e);
                return output.streamingResponse();
            }

            if (output != null && output.size() > 0) {
                var content = output.fullContent();
                var contentLength = declaredLength >= 0 ? declaredLength : content.remaining() + 1L;
                return new ProcessedResponse(observation, context, response.code(), response.headers(), body.contentType(), content, contentLength, body, e, false);
            }
            return errorResponse(observation, context, body, e);
        }
    }

    private static ProcessedResponse errorResponse(HttpServerObservation observation, Context context, @Nullable HttpBodyOutput bodyToClose, Throwable error) {
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
            null,
            false
        );
    }

    private void sendResponse(HttpServerExchange exchange, ProcessedResponse response) {
        var observation = response.observation();
        try {
            this.prepareExchange(exchange, response);
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

    private void sendStreamingResponse(HttpServerExchange exchange, ProcessedResponse response, AsyncBodyPipe pipe) {
        try {
            this.prepareExchange(exchange, response);
            if (response.contentLength() >= 0) {
                exchange.setResponseContentLength(response.contentLength());
            }
            pipe.attach(new AsyncSenderImpl(exchange));
        } catch (Throwable e) {
            pipe.abort(e);
            exchange.endExchange();
            logger.warn("HTTP streaming response setup failed", e);
        }
    }

    private void prepareExchange(HttpServerExchange exchange, ProcessedResponse response) {
        if (this.telemetryEnabled) {
            W3CTraceContextPropagator.getInstance().inject(
                response.context(),
                exchange.getResponseHeaders(),
                HttpServerExchangeMapGetter.INSTANCE
            );
            exchange.addExchangeCompleteListener((e, nextListener) -> {
                response.observation().end();
                nextListener.proceed();
            });
        }
        exchange.setStatusCode(response.code());
        exchange.getResponseHeaders().put(Headers.SERVER, "Kora");
        this.setHeaders(exchange.getResponseHeaders(), response.headers(), response.contentType());
        if (response.contentType() != null) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, response.contentType());
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
                                     @Nullable HttpBodyOutput body,
                                     @Nullable Throwable sendFailure,
                                     boolean streamingStarted) {}

    private static final class AdaptiveBodyOutputStream extends OutputStream {

        private static final int SMALL_BODY_THRESHOLD = 64 * 1024;
        private static final int STREAM_CHUNK_SIZE = 16 * 1024;

        private final KoraRequestProcessingHttpHandler handler;
        private final HttpServerExchange exchange;
        private final ProcessedResponse streamingResponse;
        private byte[] buffer = new byte[256];
        private int size;
        private @Nullable AsyncBodyPipe pipe;

        private AdaptiveBodyOutputStream(KoraRequestProcessingHttpHandler handler,
                                         HttpServerExchange exchange,
                                         ProcessedResponse streamingResponse) {
            this.handler = handler;
            this.exchange = exchange;
            this.streamingResponse = streamingResponse;
        }

        @Override
        public void write(int value) throws IOException {
            this.write(new byte[]{(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return;
            }
            if (this.pipe == null && this.size + length <= SMALL_BODY_THRESHOLD) {
                this.ensureCapacity(this.size + length);
                System.arraycopy(bytes, offset, this.buffer, this.size, length);
                this.size += length;
                return;
            }
            if (this.pipe == null) {
                this.startStreaming();
            }
            while (length > 0) {
                var chunkLength = Math.min(length, STREAM_CHUNK_SIZE);
                var chunk = Arrays.copyOfRange(bytes, offset, offset + chunkLength);
                this.pipe.offer(ByteBuffer.wrap(chunk));
                offset += chunkLength;
                length -= chunkLength;
            }
        }

        private void ensureCapacity(int required) {
            if (required <= this.buffer.length) {
                return;
            }
            var capacity = Math.min(SMALL_BODY_THRESHOLD, Math.max(required, this.buffer.length << 1));
            this.buffer = Arrays.copyOf(this.buffer, capacity);
        }

        private void startStreaming() throws IOException {
            this.pipe = new AsyncBodyPipe(this.handler, this.exchange, this.streamingResponse);
            this.pipe.start();
            if (this.size > 0) {
                this.pipe.offer(ByteBuffer.wrap(this.buffer, 0, this.size));
                this.buffer = new byte[0];
                this.size = 0;
            }
        }

        private boolean isStreaming() {
            return this.pipe != null;
        }

        private int size() {
            return this.size;
        }

        private ByteBuffer fullContent() {
            return ByteBuffer.wrap(this.buffer, 0, this.size);
        }

        private ProcessedResponse streamingResponse() {
            return this.streamingResponse;
        }

        private void completeStreaming() {
            Objects.requireNonNull(this.pipe).complete();
        }

        private void failStreaming(Throwable failure) {
            Objects.requireNonNull(this.pipe).fail(failure);
        }
    }

    private static final class AsyncBodyPipe {

        private static final int MAX_PENDING_CHUNKS = 4;

        private final KoraRequestProcessingHttpHandler handler;
        private final HttpServerExchange exchange;
        private final ProcessedResponse response;
        private final ConcurrentLinkedQueue<ByteBuffer> chunks = new ConcurrentLinkedQueue<>();
        private final Semaphore freeSlots = new Semaphore(MAX_PENDING_CHUNKS);
        private final AtomicBoolean drainScheduled = new AtomicBoolean();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final IoCallback callback = new IoCallback() {
            @Override
            public void onComplete(HttpServerExchange exchange, Sender sender) {
                freeSlots.release();
                sending = false;
                drain();
            }

            @Override
            public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                freeSlots.release();
                finishException(exception);
            }
        };

        private volatile @Nullable Sender sender;
        private volatile boolean producerComplete;
        private volatile @Nullable Throwable producerFailure;
        private boolean sending;

        private AsyncBodyPipe(KoraRequestProcessingHttpHandler handler,
                              HttpServerExchange exchange,
                              ProcessedResponse response) {
            this.handler = handler;
            this.exchange = exchange;
            this.response = response;
        }

        private void start() {
            // Keep Connectors.executeRootHandler() from auto-ending the exchange when the VT producer returns.
            this.exchange.dispatch(SameThreadExecutor.INSTANCE, () -> {});
            // dispatch(...) itself is deferred until the handler returns, so the consumer must be started directly.
            this.exchange.getIoThread().execute(() -> this.handler.sendStreamingResponse(this.exchange, this.response, this));
        }

        private void attach(Sender sender) {
            this.sender = sender;
            this.scheduleDrain();
        }

        private void offer(ByteBuffer chunk) throws IOException {
            try {
                this.freeSlots.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for HTTP response backpressure", e);
            }
            if (this.terminal.get()) {
                this.freeSlots.release();
                throw new IOException("HTTP response stream is already closed");
            }
            this.chunks.add(chunk);
            this.scheduleDrain();
        }

        private void complete() {
            this.producerComplete = true;
            this.scheduleDrain();
        }

        private void fail(Throwable failure) {
            this.producerFailure = failure;
            this.producerComplete = true;
            this.scheduleDrain();
        }

        private void abort(Throwable failure) {
            this.producerFailure = failure;
            this.producerComplete = true;
            this.finishException(failure);
        }

        private void scheduleDrain() {
            if (this.sender != null && this.drainScheduled.compareAndSet(false, true)) {
                this.exchange.getIoThread().execute(this::drain);
            }
        }

        private void drain() {
            this.drainScheduled.set(false);
            if (this.terminal.get() || this.sending) {
                return;
            }
            var chunk = this.chunks.poll();
            if (chunk != null) {
                this.sending = true;
                Objects.requireNonNull(this.sender).send(chunk, this.callback);
                return;
            }
            if (this.producerComplete) {
                var failure = this.producerFailure;
                if (failure == null) {
                    this.finishComplete();
                } else {
                    this.finishException(failure);
                }
            }
        }

        private void finishComplete() {
            if (!this.terminal.compareAndSet(false, true)) {
                return;
            }
            closeBody(this.response.observation(), this.response.body());
            IoCallback.END_EXCHANGE.onComplete(this.exchange, Objects.requireNonNull(this.sender));
        }

        private void finishException(Throwable failure) {
            if (!this.terminal.compareAndSet(false, true)) {
                return;
            }
            var abandoned = 0;
            while (this.chunks.poll() != null) {
                abandoned++;
            }
            this.freeSlots.release(abandoned + MAX_PENDING_CHUNKS);
            this.response.observation().observeError(failure);
            this.response.observation().observeResultCode(HttpResultCode.CONNECTION_ERROR);
            closeBody(this.response.observation(), this.response.body());
            try {
                this.exchange.getConnection().close();
            } catch (IOException closeException) {
                failure.addSuppressed(closeException);
            }
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

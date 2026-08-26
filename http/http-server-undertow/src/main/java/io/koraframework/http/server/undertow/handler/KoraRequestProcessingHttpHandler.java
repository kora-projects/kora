package io.koraframework.http.server.undertow.handler;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyOutput;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.HttpServerConfig;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.router.HttpServerRouter;
import io.koraframework.http.server.common.telemetry.HttpServerObservation;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.impl.NoopHttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.impl.NoopHttpServerObservation;
import io.koraframework.http.server.undertow.UndertowConfig;
import io.koraframework.http.server.undertow.UndertowContext;
import io.koraframework.http.server.undertow.request.UndertowUnroutedHttpRequest;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.server.ExchangeCompletionListener;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KoraRequestProcessingHttpHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(KoraRequestProcessingHttpHandler.class);
    private static final ByteBuffer BODY_EMPTY = ByteBuffer.allocate(0);

    private static final W3CTraceContextPropagator PROPAGATOR = W3CTraceContextPropagator.getInstance();

    private static final HttpString TRACE_PARENT = new HttpString("traceparent");
    private static final HttpString TRACE_STATE = new HttpString("tracestate");

    /**
     * Kora normalizes every header name to lower case, while Undertow's {@link Headers} cache is keyed by the
     * canonical spelling ({@code Content-Type}). Looking a lower case name up via {@link HttpString#tryFromString}
     * therefore always misses that cache and allocates a fresh {@link HttpString} plus its backing byte array for
     * every header of every response. This table restores the cache hit: {@link HttpString} hashes and compares
     * case-insensitively, so the canonical instances behave exactly like freshly built lower case ones.
     */
    private static final Map<String, HttpString> HEADER_NAMES = lowercaseHeaderNames();

    private final HttpServerTelemetry telemetry;
    private final HttpServerRouter httpServerRouter;
    private final boolean telemetryEnabled;
    private final boolean contextPropagationEnabled;

    public KoraRequestProcessingHttpHandler(ValueOf<UndertowConfig> undertowConfig,
                                            ValueOf<HttpServerConfig> config,
                                            HttpServerTelemetry telemetry,
                                            HttpServerRouter httpServerRouter) {
        this.telemetry = telemetry;
        this.httpServerRouter = httpServerRouter;
        this.telemetryEnabled = !(telemetry instanceof NoopHttpServerTelemetry);
        this.contextPropagationEnabled = this.telemetryEnabled && config.get().telemetry().tracing().contextPropagation();
    }

    private static Map<String, HttpString> lowercaseHeaderNames() {
        var names = new HashMap<String, HttpString>(256);
        names.put("accept",                    Headers.ACCEPT);
        names.put("accept-charset",            Headers.ACCEPT_CHARSET);
        names.put("accept-encoding",           Headers.ACCEPT_ENCODING);
        names.put("accept-language",           Headers.ACCEPT_LANGUAGE);
        names.put("accept-ranges",             Headers.ACCEPT_RANGES);
        names.put("age",                       Headers.AGE);
        names.put("allow",                     Headers.ALLOW);
        names.put("authentication-info",       Headers.AUTHENTICATION_INFO);
        names.put("authorization",             Headers.AUTHORIZATION);
        names.put("cache-control",             Headers.CACHE_CONTROL);
        names.put("connection",                Headers.CONNECTION);
        names.put("content-disposition",       Headers.CONTENT_DISPOSITION);
        names.put("content-encoding",          Headers.CONTENT_ENCODING);
        names.put("content-language",          Headers.CONTENT_LANGUAGE);
        names.put("content-length",            Headers.CONTENT_LENGTH);
        names.put("content-location",          Headers.CONTENT_LOCATION);
        names.put("content-md5",               Headers.CONTENT_MD5);
        names.put("content-range",             Headers.CONTENT_RANGE);
        names.put("content-security-policy",   Headers.CONTENT_SECURITY_POLICY);
        names.put("content-transfer-encoding", Headers.CONTENT_TRANSFER_ENCODING);
        names.put("content-transfer-encoding", Headers.CONTENT_TRANSFER_ENCODING);
        names.put("content-type",              Headers.CONTENT_TYPE);
        names.put("cookie",                    Headers.COOKIE);
        names.put("cookie2",                   Headers.COOKIE2);
        names.put("date",                      Headers.DATE);
        names.put("etag",                      Headers.ETAG);
        names.put("expect",                    Headers.EXPECT);
        names.put("expires",                   Headers.EXPIRES);
        names.put("forwarded",                 Headers.FORWARDED);
        names.put("from",                      Headers.FROM);
        names.put("host",                      Headers.HOST);
        names.put("if-match",                  Headers.IF_MATCH);
        names.put("if-modified-since",         Headers.IF_MODIFIED_SINCE);
        names.put("if-none-match",             Headers.IF_NONE_MATCH);
        names.put("if-range",                  Headers.IF_RANGE);
        names.put("if-unmodified-since",       Headers.IF_UNMODIFIED_SINCE);
        names.put("last-modified",             Headers.LAST_MODIFIED);
        names.put("location",                  Headers.LOCATION);
        names.put("max-forwards",              Headers.MAX_FORWARDS);
        names.put("origin",                    Headers.ORIGIN);
        names.put("pragma",                    Headers.PRAGMA);
        names.put("proxy-authenticate",        Headers.PROXY_AUTHENTICATE);
        names.put("proxy-authorization",       Headers.PROXY_AUTHORIZATION);
        names.put("range",                     Headers.RANGE);
        names.put("referer",                   Headers.REFERER);
        names.put("referrer-policy",           Headers.REFERRER_POLICY);
        names.put("refresh",                   Headers.REFRESH);
        names.put("retry-after",               Headers.RETRY_AFTER);
        names.put("sec-websocket-accept",      Headers.SEC_WEB_SOCKET_ACCEPT);
        names.put("sec-websocket-extensions",  Headers.SEC_WEB_SOCKET_EXTENSIONS);
        names.put("sec-websocket-key",         Headers.SEC_WEB_SOCKET_KEY);
        names.put("sec-websocket-key1",        Headers.SEC_WEB_SOCKET_KEY1);
        names.put("sec-websocket-key2",        Headers.SEC_WEB_SOCKET_KEY2);
        names.put("sec-websocket-location",    Headers.SEC_WEB_SOCKET_LOCATION);
        names.put("sec-websocket-origin",      Headers.SEC_WEB_SOCKET_ORIGIN);
        names.put("sec-websocket-protocol",    Headers.SEC_WEB_SOCKET_PROTOCOL);
        names.put("sec-websocket-version",     Headers.SEC_WEB_SOCKET_VERSION);
        names.put("secure_protocol",           Headers.SECURE_PROTOCOL);
        names.put("server",                    Headers.SERVER);
        names.put("servlet-engine",            Headers.SERVLET_ENGINE);
        names.put("set-cookie",                Headers.SET_COOKIE);
        names.put("set-cookie2",               Headers.SET_COOKIE2);
        names.put("ssl_cipher",                Headers.SSL_CIPHER);
        names.put("ssl_cipher_usekeysize",     Headers.SSL_CIPHER_USEKEYSIZE);
        names.put("ssl_client_cert",           Headers.SSL_CLIENT_CERT);
        names.put("ssl_session_id",            Headers.SSL_SESSION_ID);
        names.put("status",                    Headers.STATUS);
        names.put("strict-transport-security", Headers.STRICT_TRANSPORT_SECURITY);
        names.put("te",                        Headers.TE);
        names.put("trailer",                   Headers.TRAILER);
        names.put("transfer-encoding",         Headers.TRANSFER_ENCODING);
        names.put("upgrade",                   Headers.UPGRADE);
        names.put("user-agent",                Headers.USER_AGENT);
        names.put("vary",                      Headers.VARY);
        names.put("via",                       Headers.VIA);
        names.put("warning",                   Headers.WARNING);
        names.put("www-authenticate",          Headers.WWW_AUTHENTICATE);
        names.put("x-content-length",          Headers.X_CONTENT_LENGTH);
        names.put("x-content-type-options",    Headers.X_CONTENT_TYPE_OPTIONS);
        names.put("x-disable-push",            Headers.X_DISABLE_PUSH);
        names.put("x-forwarded-for",           Headers.X_FORWARDED_FOR);
        names.put("x-forwarded-host",          Headers.X_FORWARDED_HOST);
        names.put("x-forwarded-port",          Headers.X_FORWARDED_PORT);
        names.put("x-forwarded-proto",         Headers.X_FORWARDED_PROTO);
        names.put("x-forwarded-server",        Headers.X_FORWARDED_SERVER);
        names.put("x-frame-options",           Headers.X_FRAME_OPTIONS);
        names.put("x-xss-protection",          Headers.X_XSS_PROTECTION);
        names.put("traceparent",               TRACE_PARENT);
        names.put("tracestate",                TRACE_STATE);
        return Map.copyOf(names);
    }

    /**
     * @return interned {@link HttpString} for an already lower cased header name, or <i>null</i> if the name can not be
     * encoded as a header name at all.
     */
    @Nullable
    private static HttpString headerName(String lowercaseName) {
        var cached = HEADER_NAMES.get(lowercaseName);
        return cached != null
            ? cached
            : HttpString.tryFromString(lowercaseName);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        exchange.dispatch(SameThreadExecutor.INSTANCE, () -> this.process(exchange));
    }

    private void process(HttpServerExchange exchange) {
        ProcessedResponse processedResponse;
        try {
            processedResponse = this.processRequest(exchange);
        } catch (Throwable e) {
            logger.warn("HTTP request processing failed", e);
            processedResponse = this.errorResponse(exchange, NoopHttpServerObservation.INSTANCE, Context.root(), null, e);
        }
        if (!processedResponse.streamingStarted()) {
            exchange.getIoThread().execute(processedResponse);
        }
    }

    private ProcessedResponse processRequest(HttpServerExchange exchange) {
        var rootCtx = this.contextPropagationEnabled
            ? PROPAGATOR.extract(Context.root(), exchange.getRequestHeaders(), HttpServerExchangeMapGetter.INSTANCE)
            : Context.root();
        return ScopedValue
            .where(UndertowContext.VALUE, new UndertowContext(exchange))
            .where(io.koraframework.logging.common.MDC.VALUE, new io.koraframework.logging.common.MDC())
            .where(OpentelemetryContext.VALUE, rootCtx)
            .call(() -> {
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
                                    return this.errorResponse(exchange, observation, ctx, null, e);
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
            return new ProcessedResponse(this, exchange, observation, context, response.code(), response.headers(), null, null, -1, null, null, false);
        }

        var declaredLength = body.contentLength();
        if (headRequest) {
            return new ProcessedResponse(this, exchange, observation, context, response.code(), response.headers(), body.contentType(), BODY_EMPTY, declaredLength, body, null, false);
        }

        AdaptiveBodyOutputStream output = null;
        try {
            var contentType = body.contentType();
            var content = body.getFullContentIfAvailable();
            if (content != null) {
                var contentLength = declaredLength >= 0 ? declaredLength : content.remaining();
                return new ProcessedResponse(this, exchange, observation, context, response.code(), response.headers(), contentType, content, contentLength, body, null, false);
            }

            var streamingResponse = new ProcessedResponse(this, exchange, observation, context, response.code(), response.headers(), contentType, null, declaredLength, body, null, true);
            output = new AdaptiveBodyOutputStream(this, exchange, streamingResponse, declaredLength);
            // HttpBodyOutput.write() must flush whatever it wraps around this stream before returning: the buffer is
            // read back as soon as write() returns and close() is never called on the writer's behalf.
            body.write(output);
            if (output.isStreaming()) {
                output.completeStreaming();
                return streamingResponse;
            }

            content = output.fullContent();
            var contentLength = declaredLength >= 0 ? declaredLength : content.remaining();
            return new ProcessedResponse(this, exchange, observation, context, response.code(), response.headers(), contentType, content, contentLength, body, null, false);
        } catch (Throwable e) {
            if (output != null && output.isStreaming()) {
                // AsyncBodyPipe observes its own terminal failure. Once it has terminated, the next write() only
                // fails because the pipe is already closed, so reporting it here would count the error twice.
                output.failStreaming(e);
                return output.streamingResponse();
            }

            observation.observeError(e);
            if (output != null && output.size() > 0) {
                var content = output.fullContent();
                // deliberately one byte longer than what is actually sent: the client has to see the response as
                // truncated, the connection is then closed by ProcessedResponse.onComplete()
                var contentLength = declaredLength >= 0 ? declaredLength : content.remaining() + 1L;
                return new ProcessedResponse(this, exchange, observation, context, response.code(), response.headers(), body.contentType(), content, contentLength, body, e, false);
            }
            return this.errorResponse(exchange, observation, context, body, e);
        }
    }

    private ProcessedResponse errorResponse(HttpServerExchange exchange, HttpServerObservation observation, Context context, @Nullable HttpBodyOutput bodyToClose, Throwable error) {
        var message = Objects.requireNonNullElse(error.getMessage(), "Unknown error");
        var errorBody = HttpBody.plaintext(message);
        var response = observation.observeResponse(HttpServerResponse.of(500, errorBody));
        var content = errorBody.getFullContentIfAvailable();
        return new ProcessedResponse(
            this,
            exchange,
            observation,
            context,
            response.code(),
            response.headers(),
            errorBody.contentType(),
            content,
            content.remaining(),
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
            // getResponseSender() reuses the sender cached on the exchange, ProcessedResponse is its own IoCallback
            exchange.getResponseSender().send(content, response);
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
            pipe.attach(exchange.getResponseSender());
        } catch (Throwable e) {
            pipe.abort(e);
            exchange.endExchange();
            logger.warn("HTTP streaming response setup failed", e);
        }
    }

    private void prepareExchange(HttpServerExchange exchange, ProcessedResponse response) {
        var responseHeaders = exchange.getResponseHeaders();
        if (this.contextPropagationEnabled) {
            PROPAGATOR.inject(response.context(), responseHeaders, HttpServerExchangeMapGetter.INSTANCE);
        }
        if (this.telemetryEnabled) {
            exchange.addExchangeCompleteListener(response);
        }
        exchange.setStatusCode(response.code());
        responseHeaders.put(Headers.SERVER, "Kora");
        var contentType = response.contentType();
        setHeaders(responseHeaders, response.headers(), contentType);
        if (contentType != null) {
            responseHeaders.put(Headers.CONTENT_TYPE, contentType);
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

    /**
     * Carries everything needed to write one response back to the client. It implements {@link Runnable},
     * {@link IoCallback} and {@link ExchangeCompletionListener} itself so that the send path does not allocate a
     * dispatch lambda, an I/O callback and a completion listener on top of it for every single request.
     */
    private record ProcessedResponse(KoraRequestProcessingHttpHandler handler,
                                     HttpServerExchange exchange,
                                     HttpServerObservation observation,
                                     Context context,
                                     int code,
                                     HttpHeaders headers,
                                     @Nullable String contentType,
                                     @Nullable ByteBuffer content,
                                     long contentLength,
                                     @Nullable HttpBodyOutput body,
                                     @Nullable Throwable sendFailure,
                                     boolean streamingStarted) implements Runnable, IoCallback, ExchangeCompletionListener {

        @Override
        public void run() {
            this.handler.sendResponse(this.exchange, this);
        }

        @Override
        public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
            this.observation.end();
            nextListener.proceed();
        }

        @Override
        public void onComplete(HttpServerExchange exchange, Sender sender) {
            closeBody(this.observation, this.body);
            var failure = this.sendFailure;
            if (failure != null) {
                this.observation.observeResultCode(HttpResultCode.CONNECTION_ERROR);
                try {
                    exchange.getConnection().close();
                } catch (IOException closeException) {
                    failure.addSuppressed(closeException);
                }
                return;
            }
            IoCallback.END_EXCHANGE.onComplete(exchange, sender);
        }

        @Override
        public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
            this.observation.observeError(exception);
            this.observation.observeResultCode(HttpResultCode.CONNECTION_ERROR);
            closeBody(this.observation, this.body);
            IoCallback.END_EXCHANGE.onException(exchange, sender, exception);
        }
    }

    private static final class AdaptiveBodyOutputStream extends OutputStream {

        private static final int SMALL_BODY_THRESHOLD = 64 * 1024;
        private static final int STREAM_CHUNK_SIZE = 16 * 1024;
        private static final int MIN_BUFFER_SIZE = 64;
        private static final int DEFAULT_BUFFER_SIZE = 128;
        private static final byte[] EMPTY_BUFFER = new byte[0];

        private final KoraRequestProcessingHttpHandler handler;
        private final HttpServerExchange exchange;
        private final ProcessedResponse streamingResponse;
        private byte[] buffer;
        private int size;
        private @Nullable AsyncBodyPipe pipe;

        private AdaptiveBodyOutputStream(KoraRequestProcessingHttpHandler handler,
                                         HttpServerExchange exchange,
                                         ProcessedResponse streamingResponse,
                                         long declaredLength) {
            this.handler = handler;
            this.exchange = exchange;
            this.streamingResponse = streamingResponse;
            this.buffer = new byte[initialCapacity(declaredLength)];
        }

        /**
         * Most bodies declare their length up front, so the buffer can be sized once instead of doubling up from a
         * fixed 256 bytes (an 8 KiB body used to cost 6 allocations and 5 array copies).
         */
        private static int initialCapacity(long declaredLength) {
            if (declaredLength < 0) {
                return DEFAULT_BUFFER_SIZE;
            }
            if (declaredLength > SMALL_BODY_THRESHOLD) {
                return MIN_BUFFER_SIZE;
            }
            return (int) Math.max(MIN_BUFFER_SIZE, declaredLength);
        }

        @Override
        public void write(int value) throws IOException {
            if (this.pipe == null && this.size < SMALL_BODY_THRESHOLD) {
                this.ensureCapacity(this.size + 1);
                this.buffer[this.size++] = (byte) value;
                return;
            }
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
                this.buffer = EMPTY_BUFFER;
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
            // handleRequest() already took the dispatch slot, so executeRootHandler() will not auto-end the exchange
            // and the producer is off-call by the time the consumer touches the sender.
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

    private static void setHeaders(HeaderMap responseHeaders, HttpHeaders headers, @Nullable String contentType) {
        for (var header : headers) {
            var key = header.getKey();
            if (isReservedHeader(key, contentType)) {
                continue;
            }
            var name = headerName(key);
            if (name == null) {
                logger.warn("HTTP response header with unsupported name was skipped: {}", key);
                continue;
            }
            responseHeaders.addAll(name, header.getValue());
        }
    }

    private static boolean isReservedHeader(String key, @Nullable String contentType) {
        return switch (key) {
            case "server", "content-length", "transfer-encoding" -> true;
            case "content-type" -> contentType != null;
            default -> false;
        };
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
            var name = headerName(key);
            if (name != null) {
                headers.add(name, value);
            }
        }
    }
}

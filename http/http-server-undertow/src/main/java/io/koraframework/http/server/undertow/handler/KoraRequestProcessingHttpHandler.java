package io.koraframework.http.server.undertow.handler;

import io.koraframework.application.graph.*;
import io.koraframework.common.context.*;
import io.koraframework.http.common.*;
import io.koraframework.http.common.body.*;
import io.koraframework.http.common.header.*;
import io.koraframework.http.server.common.*;
import io.koraframework.http.server.common.response.*;
import io.koraframework.http.server.common.router.*;
import io.koraframework.http.server.common.telemetry.*;
import io.koraframework.http.server.common.telemetry.impl.*;
import io.koraframework.http.server.undertow.*;
import io.koraframework.http.server.undertow.request.*;
import io.opentelemetry.api.trace.propagation.*;
import io.opentelemetry.context.*;
import io.opentelemetry.context.propagation.*;
import io.undertow.io.*;
import io.undertow.server.*;
import io.undertow.util.*;
import org.jspecify.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import static io.koraframework.common.telemetry.Observation.*;
import static io.koraframework.common.telemetry.OpentelemetryContextStorage.*;
import static io.koraframework.logging.common.MDC.*;

public final class KoraRequestProcessingHttpHandler implements HttpHandler {

    private static final Logger logger = LoggerFactory.getLogger(KoraRequestProcessingHttpHandler.class);
    private static final ByteBuffer BODY_EMPTY = ByteBuffer.allocate(0);

    private static final W3CTraceContextPropagator PROPAGATOR = W3CTraceContextPropagator.getInstance();

    public static final KoraContext.Key<UndertowContext> UNDERTOW_KEY = KoraContext.Key.create();

    private final HttpServerConfig httpServerConfig;
    private final HttpServerTelemetry telemetry;
    private final HttpServerRouter httpServerRouter;
    private final boolean telemetryEnabled;
    private final boolean contextPropagationEnabled;

    public KoraRequestProcessingHttpHandler(ValueOf<UndertowConfig> undertowConfig,
                                            HttpServerConfig httpServerConfig,
                                            HttpServerRouter httpServerRouter,
                                            HttpServerTelemetry telemetry) {
        this.telemetry = telemetry;
        this.httpServerRouter = httpServerRouter;
        this.telemetryEnabled = !(telemetry instanceof NoopHttpServerTelemetry);
        this.contextPropagationEnabled = telemetryEnabled;
        this.httpServerConfig = httpServerConfig;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        exchange.dispatch(SameThreadExecutor.INSTANCE, new ProcessRequestRunnable(this, exchange));
    }

    private void process(HttpServerExchange exchange) {
        ProcessedResponse processedResponse;
        try {
            processedResponse = processRequest(exchange);
        } catch (Throwable e) {
            logger.warn("HTTP request processing failed", e);
            processedResponse = errorResponse(exchange, NoopHttpServerObservation.INSTANCE, Context.root(), null, e);
        }
        if (!processedResponse.streamingStarted()) {
            exchange.getIoThread().execute(processedResponse);
        }
    }

private ProcessedResponse processRequest(HttpServerExchange exchange) {
var rootCtx = contextPropagationEnabled
? PROPAGATOR.extract(Context.root(), exchange.getRequestHeaders(), HttpServerExchangeMapGetter.INSTANCE)
: Context.root();

var request = new UndertowUnroutedHttpRequest(exchange);
var invocation = httpServerRouter.route(request);
var observation = telemetry.observe(invocation.routedRequest());
var ctx = rootCtx.with(observation.span());

var finalContext = KoraContext.empty()
.with(UNDERTOW_KEY, new UndertowContext(exchange))
.with(MDC_KEY, new io.koraframework.logging.common.MDC())
.with(OPENTELEMETRY_KEY, ctx)
.with(OBSERVATION_KEY, observation);

return KoraContext.runWith(finalContext, new RequestProcessingRunner(this, exchange, ctx, invocation, observation));
}

    private static final class RequestProcessingRunner implements Supplier<ProcessedResponse> {
        private final KoraRequestProcessingHttpHandler handler;
        private final HttpServerExchange exchange;
        private final Context ctx;
        private final HttpServerInvocation invocation;
        private final HttpServerObservation observation;

        public RequestProcessingRunner(KoraRequestProcessingHttpHandler handler,
                                       HttpServerExchange exchange,
                                       Context ctx,
                                       HttpServerInvocation invocation,
                                       HttpServerObservation observation) {
            this.handler = handler;
            this.exchange = exchange;
            this.ctx = ctx;
            this.invocation = invocation;
            this.observation = observation;
        }

        @Override
        public ProcessedResponse get() {
            try {
                var httpServerRequest = observation.observeRequest(invocation.routedRequest());
                var response = invocation.proceed(httpServerRequest);
                return handler.prepareResponse(exchange, observation, ctx, response, exchange.getRequestMethod().equals(Methods.HEAD));
            } catch (Throwable e) {
                observation.observeError(e);
                if (e instanceof HttpServerResponse rs) {
                    return handler.prepareResponse(exchange, observation, ctx, rs, exchange.getRequestMethod().equals(Methods.HEAD));
                } else {
                    return handler.errorResponse(exchange, observation, ctx, null, e);
                }
            } finally {
                org.slf4j.MDC.clear();
            }
        }
    }

    private static final class ObservationProcessingRunner implements Supplier<ProcessedResponse> {
        private final KoraRequestProcessingHttpHandler handler;
        private final HttpServerInvocation invocation;
        private final HttpServerObservation observation;
        private final HttpServerExchange exchange;
        private final Context ctx;

        public ObservationProcessingRunner(KoraRequestProcessingHttpHandler handler,
                                           HttpServerInvocation invocation,
                                           HttpServerObservation observation,
                                           HttpServerExchange exchange,
                                           Context ctx) {
            this.handler = handler;
            this.invocation = invocation;
            this.observation = observation;
            this.exchange = exchange;
            this.ctx = ctx;
        }

        @Override
        public ProcessedResponse get() {
            HttpServerResponse response;
            try {
                var httpServerRequest = observation.observeRequest(invocation.routedRequest());
                response = invocation.proceed(httpServerRequest);
            } catch (Throwable e) {
                observation.observeError(e);
                if (e instanceof HttpServerResponse rs) {
                    response = rs;
                } else {
                    return handler.errorResponse(exchange, observation, ctx, null, e);
                }
            }
            return handler.prepareResponse(exchange, observation, ctx, response, exchange.getRequestMethod().equals(Methods.HEAD));
        }
    }

    private static final class ProcessRequestRunnable implements Runnable {
        private final KoraRequestProcessingHttpHandler handler;
        private final HttpServerExchange exchange;

        public ProcessRequestRunnable(KoraRequestProcessingHttpHandler handler, HttpServerExchange exchange) {
            this.handler = handler;
            this.exchange = exchange;
        }

        @Override
        public void run() {
            handler.process(exchange);
        }
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
                output.failStreaming(e);
                return output.streamingResponse();
            }

            observation.observeError(e);
            if (output != null && output.size() > 0) {
                var content = output.fullContent();
                var contentLength = declaredLength >= 0 ? declaredLength : content.remaining() + 1L;
                return new ProcessedResponse(this, exchange, observation, context, response.code(), response.headers(), body.contentType(), content, contentLength, body, e, false);
            }
            return errorResponse(exchange, observation, context, body, e);
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
            prepareExchange(exchange, response);
            var content = response.content();
            if (content == null) {
                closeBody(observation, response.body());
                exchange.endExchange();
                return;
            }
            exchange.setResponseContentLength(response.contentLength());
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
            prepareExchange(exchange, response);
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
        if (contextPropagationEnabled) {
            PROPAGATOR.inject(response.context(), responseHeaders, HttpServerExchangeMapGetter.INSTANCE);
        }
        if (telemetryEnabled) {
            exchange.addExchangeCompleteListener(response);
        }
        exchange.setStatusCode(response.code());
        var contentType = response.contentType();
        setHeaders(responseHeaders, response.headers(), contentType);
        if (contentType != null) {
            responseHeaders.put(Headers.CONTENT_TYPE, contentType);
        }
    }

    private void setHeaders(HeaderMap responseHeaders, HttpHeaders headers, @Nullable String contentType) {
        if (httpServerConfig.headerServerNameEnabled()) {
            responseHeaders.put(Headers.SERVER, "Kora");
        }
        for (var header : headers) {
            var key = header.getKey();
            if (isReservedHeader(key, contentType)) {
                continue;
            }
            var name = HttpString.tryFromString(key);
            if (name == null) {
                logger.warn("HTTP response header with unsupported name was skipped: {}", key);
                continue;
            }
            responseHeaders.addAll(name, header.getValue());
        }
    }

    private static boolean isReservedHeader(String key, @Nullable String contentType) {
        if ("server".equals(key) || "content-length".equals(key) || "transfer-encoding".equals(key)) {
            return true;
        } else if ("content-type".equals(key)) {
            return contentType != null;
        } else {
            return false;
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
            handler.sendResponse(exchange, this);
        }

        @Override
        public void exchangeEvent(HttpServerExchange exchange, NextListener nextListener) {
            observation.end();
            nextListener.proceed();
        }

        @Override
        public void onComplete(HttpServerExchange exchange, Sender sender) {
            closeBody(observation, body);
            var failure = sendFailure;
            if (failure != null) {
                observation.observeResultCode(HttpResultCode.CONNECTION_ERROR);
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
            observation.observeError(exception);
            observation.observeResultCode(HttpResultCode.CONNECTION_ERROR);
            closeBody(observation, body);
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
            buffer = new byte[initialCapacity(declaredLength)];
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
            if (pipe == null && size < SMALL_BODY_THRESHOLD) {
                ensureCapacity(size + 1);
                buffer[size++] = (byte) value;
                return;
            }
            write(new byte[] {(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return;
            }
            if (pipe == null && size + length <= SMALL_BODY_THRESHOLD) {
                ensureCapacity(size + length);
                System.arraycopy(bytes, offset, buffer, size, length);
                size += length;
                return;
            }
            if (pipe == null) {
                startStreaming();
            }
            while (length > 0) {
                var chunkLength = Math.min(length, STREAM_CHUNK_SIZE);
                var chunk = Arrays.copyOfRange(bytes, offset, offset + chunkLength);
                pipe.offer(ByteBuffer.wrap(chunk));
                offset += chunkLength;
                length -= chunkLength;
            }
        }

        private void ensureCapacity(int required) {
            if (required <= buffer.length) {
                return;
            }
            var capacity = Math.min(SMALL_BODY_THRESHOLD, Math.max(required, buffer.length << 1));
            buffer = Arrays.copyOf(buffer, capacity);
        }

        private void startStreaming() throws IOException {
            pipe = new AsyncBodyPipe(handler, exchange, streamingResponse);
            pipe.start();
            if (size > 0) {
                pipe.offer(ByteBuffer.wrap(buffer, 0, size));
                buffer = EMPTY_BUFFER;
                size = 0;
            }
        }

        private boolean isStreaming() {
            return pipe != null;
        }

        private int size() {
            return size;
        }

        private ByteBuffer fullContent() {
            return ByteBuffer.wrap(buffer, 0, size);
        }

        private ProcessedResponse streamingResponse() {
            return streamingResponse;
        }

        private void completeStreaming() {
            Objects.requireNonNull(pipe).complete();
        }

        private void failStreaming(Throwable failure) {
            Objects.requireNonNull(pipe).fail(failure);
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
        private final IoCallback callback;

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
            this.callback = new PipeIoCallback(this);
        }

        private void start() {
            // handleRequest() already took the dispatch slot, so executeRootHandler() will not auto-end the exchange
            // and the producer is off-call by the time the consumer touches the sender.
            exchange.getIoThread().execute(new StartStreamingRunnable(this));
        }

        private void attach(Sender sender) {
            this.sender = sender;
            scheduleDrain();
        }

        private void offer(ByteBuffer chunk) throws IOException {
            try {
                freeSlots.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for HTTP response backpressure", e);
            }
            if (terminal.get()) {
                freeSlots.release();
                throw new IOException("HTTP response stream is already closed");
            }
            chunks.add(chunk);
            scheduleDrain();
        }

        private void complete() {
            producerComplete = true;
            scheduleDrain();
        }

        private void fail(Throwable failure) {
            producerFailure = failure;
            producerComplete = true;
            scheduleDrain();
        }

        private void abort(Throwable failure) {
            producerFailure = failure;
            producerComplete = true;
            finishException(failure);
        }

        private void scheduleDrain() {
            if (sender != null && drainScheduled.compareAndSet(false, true)) {
                exchange.getIoThread().execute(new DrainRunnable(this));
            }
        }

        private void drain() {
            drainScheduled.set(false);
            if (terminal.get() || sending) {
                return;
            }
            var chunk = chunks.poll();
            if (chunk != null) {
                sending = true;
                Objects.requireNonNull(sender).send(chunk, callback);
                return;
            }
            if (producerComplete) {
                var failure = producerFailure;
                if (failure == null) {
                    finishComplete();
                } else {
                    finishException(failure);
                }
            }
        }

        private void finishComplete() {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            closeBody(response.observation(), response.body());
            IoCallback.END_EXCHANGE.onComplete(exchange, Objects.requireNonNull(sender));
        }

        private void finishException(Throwable failure) {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            var abandoned = 0;
            while (chunks.poll() != null) {
                abandoned++;
            }
            freeSlots.release(abandoned + MAX_PENDING_CHUNKS);
            response.observation().observeError(failure);
            response.observation().observeResultCode(HttpResultCode.CONNECTION_ERROR);
            closeBody(response.observation(), response.body());
            try {
                exchange.getConnection().close();
            } catch (IOException closeException) {
                failure.addSuppressed(closeException);
            }
        }

        private static final class PipeIoCallback implements IoCallback {
            private final AsyncBodyPipe pipe;

            public PipeIoCallback(AsyncBodyPipe pipe) {
                this.pipe = pipe;
            }

            @Override
            public void onComplete(HttpServerExchange exchange, Sender sender) {
                pipe.freeSlots.release();
                pipe.sending = false;
                pipe.drain();
            }

            @Override
            public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {
                pipe.freeSlots.release();
                pipe.finishException(exception);
            }
        }

        private static final class StartStreamingRunnable implements Runnable {
            private final AsyncBodyPipe pipe;

            public StartStreamingRunnable(AsyncBodyPipe pipe) {
                this.pipe = pipe;
            }

            @Override
            public void run() {
                pipe.handler.sendStreamingResponse(pipe.exchange, pipe.response, pipe);
            }
        }

        private static final class DrainRunnable implements Runnable {
            private final AsyncBodyPipe pipe;

            public DrainRunnable(AsyncBodyPipe pipe) {
                this.pipe = pipe;
            }

            @Override
            public void run() {
                pipe.drain();
            }
        }
    }

    public static class HttpServerExchangeMapGetter implements TextMapGetter<HeaderMap>, TextMapSetter<HeaderMap> {
        public static final HttpServerExchangeMapGetter INSTANCE = new HttpServerExchangeMapGetter();

        @Override
        public Iterable<String> keys(HeaderMap header) {
            return new HeaderMapIterable(header);
        }

        @Override
        public String get(HeaderMap headers, String key) {
            return headers.getFirst(key);
        }

        @Override
        public void set(HeaderMap headers, String key, String value) {
            var name = HttpString.tryFromString(key);
            if (name != null) {
                headers.add(name, value);
            }
        }

        private static final class HeaderMapIterable implements Iterable<String> {
            private final HeaderMap header;

            public HeaderMapIterable(HeaderMap header) {
                this.header = header;
            }

            @Override
            public Iterator<String> iterator() {
                return new HeaderMapIterator(header.iterator());
            }
        }

        private static final class HeaderMapIterator implements Iterator<String> {
            private final Iterator<HeaderValues> iterator;

            public HeaderMapIterator(Iterator<HeaderValues> iterator) {
                this.iterator = iterator;
            }

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public String next() {
                return iterator.next().getHeaderName().toString();
            }
        }
    }
}

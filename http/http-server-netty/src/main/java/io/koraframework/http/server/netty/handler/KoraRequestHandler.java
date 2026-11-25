package io.koraframework.http.server.netty.handler;

import io.koraframework.application.graph.*;
import io.koraframework.common.context.*;
import io.koraframework.http.common.body.*;
import io.koraframework.http.server.common.*;
import io.koraframework.http.server.common.request.*;
import io.koraframework.http.server.common.response.*;
import io.koraframework.http.server.common.router.*;
import io.koraframework.http.server.common.telemetry.*;
import io.koraframework.http.server.netty.*;
import io.koraframework.http.server.netty.body.*;
import io.koraframework.http.server.netty.buffer.*;
import io.koraframework.http.server.netty.request.*;
import io.koraframework.logging.common.MDC;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.handler.codec.*;
import io.netty.handler.codec.compression.*;
import io.netty.handler.codec.http.*;
import io.netty.util.*;
import io.opentelemetry.api.trace.propagation.*;
import io.opentelemetry.context.*;
import org.jspecify.annotations.*;
import org.slf4j.*;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static io.koraframework.common.telemetry.Observation.*;
import static io.koraframework.common.telemetry.OpentelemetryContextStorage.*;
import static io.koraframework.http.server.netty.NettyContext.*;
import static io.koraframework.logging.common.MDC.*;

public final class KoraRequestHandler implements RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(KoraRequestHandler.class);

    public static final HttpHeadersFactory REQUEST_HEADERS_FACTORY = new RequestHeadersFactory(8);
    public static final HttpHeadersFactory RESPONSE_HEADERS_FACTORY = new RequestHeadersFactory(4);
    private static final W3CTraceContextPropagator PROPAGATOR = W3CTraceContextPropagator.getInstance();
    private static final AsciiString HEADER_SERVER_VALUE = AsciiString.cached("Kora");
    private static final AsciiString CONTENT_TYPE_JSON_UTF8 = AsciiString.cached("application/json; charset=utf-8");

    private final boolean headerServerNameEnabled;
    private final boolean rejectUnsupportedHttpVersions;
    private final int directToHeapThreshold;
    private final boolean contextPropagationEnabled;
    private final HttpServerRouter currentRouter;
    private final HttpServerTelemetry telemetry;
    private final ThreadFactory virtualThreadFactory;

    public KoraRequestHandler(
        ValueOf<HttpServerConfig> httpServerConfig,
        ValueOf<KoraNettyServerConfig> nettyServerConfig,
        boolean contextPropagationEnabled,
        HttpServerRouter currentRouter,
        HttpServerTelemetry telemetry,
        ThreadFactory virtualThreadFactory
    ) {
        var httpCfg = Objects.requireNonNull(httpServerConfig, "httpServerConfig").get();
        var nettyCfg = Objects.requireNonNull(nettyServerConfig, "nettyServerConfig").get();
        this.headerServerNameEnabled = httpCfg.headerServerNameEnabled();
        this.rejectUnsupportedHttpVersions = nettyCfg.rejectUnsupportedHttpVersions();
        this.directToHeapThreshold = nettyCfg.directToHeapThreshold();
        this.contextPropagationEnabled = contextPropagationEnabled;
        this.currentRouter = Objects.requireNonNull(currentRouter, "currentRouter");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.virtualThreadFactory = Objects.requireNonNull(virtualThreadFactory, "virtualThreadFactory");
    }

    @Override
    public void accept(ChannelHandlerContext ctx, HttpRequest request, @Nullable AutoCloseable body, KoraOutboundAccess outboundAccess) {
        var version = request.protocolVersion();

        if (rejectUnsupportedHttpVersions && version.majorVersion() != 1) {
            closeQuietly(body);
            outboundAccess.closeAfterWrite();
            writeSimpleError(ctx, outboundAccess, HttpVersion.HTTP_1_1, HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED, "Unsupported HTTP version: " + version.text());
            return;
        }

        if (request.decoderResult().isFailure()) {
            closeQuietly(body);
            outboundAccess.closeAfterWrite();
            var cause = request.decoderResult().cause();
            var status = cause instanceof TooLongFrameException ? HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE : HttpResponseStatus.BAD_REQUEST;
            writeSimpleError(ctx, outboundAccess, version, status, Objects.requireNonNullElse(cause.getMessage(), "Malformed HTTP request"));
            return;
        }

        NettyUnroutedHttpRequest unroutedRequest;
        try {
            unroutedRequest = new NettyUnroutedHttpRequest(request, body, directToHeapThreshold);
        } catch (IllegalArgumentException e) {
            closeQuietly(body);
            outboundAccess.closeAfterWrite();
            writeSimpleError(ctx, outboundAccess, version, HttpResponseStatus.BAD_REQUEST, Objects.requireNonNullElse(e.getMessage(), "Invalid request URI"));
            return;
        }

        try {
            var thread = virtualThreadFactory.newThread(new RequestDispatchTask(ctx, request, unroutedRequest, outboundAccess));
            thread.start();
        } catch (Throwable t) {
            log.error("CRITICAL: Failed to create or start virtual thread", t);
            unroutedRequest.close();
            writeFallbackResponse(outboundAccess, t);
        }
    }

    private final class RequestDispatchTask implements Runnable {
        private final ChannelHandlerContext ctx;
        private final HttpRequest request;
        private final NettyUnroutedHttpRequest unroutedRequest;
        private final KoraOutboundAccess outboundAccess;

        private @Nullable HttpServerInvocation invocation;
        private @Nullable HttpServerRequest httpServerRequestObserved;
        private @Nullable HttpServerObservation observation;
        private @Nullable Context activeCtx;

        private RequestDispatchTask(
            ChannelHandlerContext ctx,
            HttpRequest request,
            NettyUnroutedHttpRequest unroutedRequest,
            KoraOutboundAccess outboundAccess) {
            this.ctx = ctx;
            this.request = request;
            this.unroutedRequest = unroutedRequest;
            this.outboundAccess = outboundAccess;
        }

        @Override
        public void run() {
            if (this.invocation != null) {
                executeAndSend(
                    ctx,
                    invocation,
                    Objects.requireNonNull(httpServerRequestObserved),
                    Objects.requireNonNull(observation),
                    request,
                    outboundAccess,
                    Objects.requireNonNull(activeCtx)
                );
                return;
            }
            var current = Thread.currentThread();
            outboundAccess.attachWorkerThread(current);
            try (unroutedRequest) {
                dispatch(this);
            } catch (Throwable t) {
                log.error("CRITICAL: Error inside Kora dispatch pipeline (Loom Thread)", t);
                outboundAccess.abort();
            } finally {
                outboundAccess.detachWorkerThread();
            }
        }
    }

    private void dispatch(RequestDispatchTask task) {
        var nettyReq = task.request;
        var ctx = task.ctx;
        var outboundAccess = task.outboundAccess;

        var invocation = currentRouter.route(task.unroutedRequest);
        var httpServerRequest = invocation.routedRequest();
        var observation = telemetry.observe(httpServerRequest);
        outboundAccess.attachment(observation);

        var httpServerRequestObserved = observation.observeRequest(httpServerRequest);
        var rootCtx = contextPropagationEnabled
            ? PROPAGATOR.extract(Context.root(), nettyReq.headers(), NettyHttpHeaderMapExchange.INSTANCE)
            : Context.root();
        var activeCtx = rootCtx.with(observation.span());

        var finalContext = KoraContext.empty()
            .with(NETTY_KEY, new NettyContext(nettyReq, ctx))
            .with(MDC_KEY, new MDC())
            .with(OPENTELEMETRY_KEY, activeCtx)
            .with(OBSERVATION_KEY, observation);

        task.invocation = invocation;
        task.httpServerRequestObserved = httpServerRequestObserved;
        task.observation = observation;
        task.activeCtx = activeCtx;

        KoraContext.runWith(finalContext, task);
    }

    private void executeAndSend(
        ChannelHandlerContext ctx,
        HttpServerInvocation invocation,
        HttpServerRequest httpServerRequestObserved,
        HttpServerObservation observation,
        HttpRequest nettyReq,
        KoraOutboundAccess outboundAccess,
        Context activeCtx
    ) {
        HttpServerResponse response;
        try {
            response = invocation.proceed(httpServerRequestObserved);
            observation.observeResponse(response);
        } catch (Throwable e) {
            if (e instanceof HttpServerResponse resp) {
                response = resp;
                observation.observeError(e);
                observation.observeResponse(response);
            } else {
                log.error("Internal server error during request dispatching", e);
                var errorResponse = HttpServerResponse.of(500);
                observation.observeError(e);
                observation.observeResponse(errorResponse);
                sendStaticErrorResponse(ctx, outboundAccess, nettyReq.protocolVersion(), activeCtx, e);
                return;
            }
        }

        try {
            prepareAndSendResponse(ctx, nettyReq, response, outboundAccess);
        } catch (Throwable t) {
            log.error("Failed to prepare or send HTTP response", t);
            observation.observeError(t);
            var body = response.body();
            if (body != null) {
                try {
                    body.close();
                } catch (IOException ignored) {
                }
            }
            writeFallbackResponse(outboundAccess, t);
        }
    }

    private void prepareAndSendResponse(
        ChannelHandlerContext ctx,
        HttpRequest nettyReq,
        HttpServerResponse response,
        KoraOutboundAccess outboundAccess
    ) throws IOException {
        var version = nettyReq.protocolVersion();
        var status = HttpResponseStatus.valueOf(response.code());
        boolean isHead = nettyReq.method() == HttpMethod.HEAD;

        if (nettyReq.decoderResult().isFailure() || response.code() >= 500) {
            outboundAccess.closeAfterWrite();
        }

        var body = response.body();
        if (body == null) {
            var nettyResp = new DefaultFullHttpResponse(version, status, Unpooled.EMPTY_BUFFER, RESPONSE_HEADERS_FACTORY.newHeaders(), EmptyHttpHeaders.INSTANCE);
            writeHeaders(nettyResp.headers(), response, null);
            outboundAccess.write(nettyResp);
            return;
        }

        var declaredLength = body.contentLength();
        var contentType = body.contentType();

        if (isHead) {
            try (body) {
                var nettyResp = new DefaultHttpResponse(version, status, RESPONSE_HEADERS_FACTORY);
                writeHeaders(nettyResp.headers(), response, contentType);
                if (declaredLength >= 0) {
                    nettyResp.headers().set(HttpHeaderNames.CONTENT_LENGTH, declaredLength);
                }
                outboundAccess.writeHeadResponse(nettyResp);
            }
            return;
        }

        var content = body.getFullContentIfAvailable();
        if (content != null) {
            try (body) {
                var buf = content.hasRemaining() ? Unpooled.wrappedBuffer(content) : Unpooled.EMPTY_BUFFER;
                var nettyResp = new DefaultFullHttpResponse(version, status, buf, RESPONSE_HEADERS_FACTORY.newHeaders(), EmptyHttpHeaders.INSTANCE);
                writeHeaders(nettyResp.headers(), response, contentType);
                outboundAccess.write(nettyResp);
            }
            return;
        }

        var sink = new NettyHttpBodySink(ctx.alloc());
        boolean written;
        try {
            written = body.writeTo(sink);
        } catch (Throwable t) {
            sink.releaseIfAllocated();
            throw t;
        }
        if (written && sink.buf != null) {
            try (body) {
                var nettyResp = new DefaultFullHttpResponse(version, status, sink.buf, RESPONSE_HEADERS_FACTORY.newHeaders(), EmptyHttpHeaders.INSTANCE);
                writeHeaders(nettyResp.headers(), response, contentType);
                outboundAccess.write(nettyResp);
            }
            return;
        }
        sink.releaseIfAllocated();

        var nettyResp = new DefaultHttpResponse(version, status, RESPONSE_HEADERS_FACTORY);
        writeHeaders(nettyResp.headers(), response, contentType);
        if (declaredLength >= 0) {
            nettyResp.headers().set(HttpHeaderNames.CONTENT_LENGTH, declaredLength);
        }

        try (body; var os = new OptimisticBodyOutputStream(ctx.alloc(), nettyResp, outboundAccess)) {
            body.write(os);
            os.finish();
        }
    }

    private void writeHeaders(HttpHeaders nettyHeaders, HttpServerResponse response, @Nullable String contentType) {
        if (headerServerNameEnabled) {
            nettyHeaders.add(HttpHeaderNames.SERVER, HEADER_SERVER_VALUE);
        }
        var respHeaders = response.headers();
        if (!respHeaders.isEmpty()) {
            for (var header : respHeaders) {
                var key = header.getKey();
                if (HttpHeaderNames.SERVER.contentEqualsIgnoreCase(key)
                    || HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(key)
                    || HttpHeaderNames.TRANSFER_ENCODING.contentEqualsIgnoreCase(key)
                    || (HttpHeaderNames.CONTENT_TYPE.contentEqualsIgnoreCase(key) && contentType != null)) {
                    continue;
                }
                nettyHeaders.add(key, header.getValue());
            }
        }
        if (contentType != null) {
            if (HttpHeaderValues.APPLICATION_JSON.contentEquals(contentType)) {
                nettyHeaders.add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);
            } else if (CONTENT_TYPE_JSON_UTF8.contentEquals(contentType)) {
                nettyHeaders.add(HttpHeaderNames.CONTENT_TYPE, CONTENT_TYPE_JSON_UTF8);
            } else {
                nettyHeaders.add(HttpHeaderNames.CONTENT_TYPE, contentType);
            }
        }
    }

    private void sendStaticErrorResponse(
        ChannelHandlerContext ctx,
        KoraOutboundAccess outboundAccess,
        HttpVersion version,
        Context context,
        Throwable t
    ) {
        var message = Objects.requireNonNullElse(t.getMessage(), "Unknown error");
        var buffer = ByteBufUtil.writeUtf8(ctx.alloc(), message);
        var errorResp = new DefaultFullHttpResponse(version, HttpResponseStatus.INTERNAL_SERVER_ERROR, buffer, RESPONSE_HEADERS_FACTORY.newHeaders(), EmptyHttpHeaders.INSTANCE);
        errorResp.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
        if (contextPropagationEnabled) {
            PROPAGATOR.inject(context, errorResp.headers(), NettyHttpHeaderMapExchange.INSTANCE);
        }
        outboundAccess.closeAfterWrite();
        outboundAccess.write(errorResp);
    }

    private static void writeSimpleError(
        ChannelHandlerContext ctx,
        KoraOutboundAccess outboundAccess,
        HttpVersion version,
        HttpResponseStatus status,
        String message
    ) {
        var buffer = ByteBufUtil.writeUtf8(ctx.alloc(), message);
        var errorResp = new DefaultFullHttpResponse(version, status, buffer, RESPONSE_HEADERS_FACTORY.newHeaders(), EmptyHttpHeaders.INSTANCE);
        errorResp.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
        outboundAccess.write(errorResp);
    }

    private static void writeFallbackResponse(KoraOutboundAccess outboundAccess, Throwable failure) {
        try {
            outboundAccess.closeAfterWrite();
            var fallbackResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.EMPTY_BUFFER, RESPONSE_HEADERS_FACTORY.newHeaders(), EmptyHttpHeaders.INSTANCE);
            outboundAccess.write(fallbackResp);
        } catch (Throwable e) {
            failure.addSuppressed(e);
            try {
                outboundAccess.abort();
            } catch (Throwable g) {
                failure.addSuppressed(g);
            }
        }
    }

    @Override
    public void responseWritten(@Nullable Object attachment) {
        if (attachment instanceof HttpServerObservation observation) {
            observation.end();
        }
    }

    @Override
    public void responseFailed(@Nullable Object attachment, Throwable cause) {
        if (attachment instanceof HttpServerObservation observation) {
            observation.observeError(cause);
            observation.end();
        }
    }

    @Override
    public void handleUnboundError(Throwable cause) {
        if (isIgnorable(cause)) {
            if (log.isDebugEnabled()) {
                log.debug("Swallowed an IOException caused by client connectivity: {}", cause.getMessage(), cause);
            }
            return;
        }
        if (cause instanceof DecompressionException) {
            if (log.isDebugEnabled()) {
                log.debug("Netty HTTP Server Error - Decompression failed: {}", cause.getMessage(), cause);
            }
        } else {
            log.error("Netty HTTP Server Error - No request state present: {}", cause.getMessage(), cause);
        }
    }

    private static void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean isIgnorable(Throwable cause) {
        if (cause instanceof ClosedChannelException || cause.getCause() instanceof ClosedChannelException) {
            return true;
        }
        var message = cause.getMessage();
        if (!(cause instanceof IOException) || message == null) {
            return false;
        }
        return containsIgnoreCase(message, "broken pipe")
            || containsConnectionError(message);
    }

    private static boolean containsConnectionError(String text) {
        var prefix = "connection ";
        int max = text.length() - prefix.length();
        for (int i = 0; i <= max; i++) {
            if (text.regionMatches(true, i, prefix, 0, prefix.length())) {
                int next = i + prefix.length();
                if (text.regionMatches(true, next, "reset", 0, 5)
                    || text.regionMatches(true, next, "closed", 0, 6)
                    || text.regionMatches(true, next, "abort", 0, 5)
                    || text.regionMatches(true, next, "broken", 0, 6)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String text, String target) {
        int max = text.length() - target.length();
        for (int i = 0; i <= max; i++) {
            if (text.regionMatches(true, i, target, 0, target.length())) {
                return true;
            }
        }
        return false;
    }

    private static final class BackpressuredOutputStream extends OutputStream implements Flow.Publisher<KoraReadBuffer>, Flow.Subscription {
        private static final int CHUNK_SIZE = 8192;
        private static final Flow.Subscription NOOP_SUBSCRIPTION = new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        };

        private final ByteBufAllocator alloc;
        private final Semaphore demand = new Semaphore(0);
        private final AtomicBoolean subscribed = new AtomicBoolean(false);
        private volatile Flow.@Nullable Subscriber<? super KoraReadBuffer> subscriber;
        private volatile boolean cancelled;
        @Nullable
        private ByteBuf current;

        BackpressuredOutputStream(ByteBufAllocator alloc) {
            this.alloc = alloc;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super KoraReadBuffer> sub) {
            Objects.requireNonNull(sub, "sub");
            if (!subscribed.compareAndSet(false, true)) {
                sub.onSubscribe(NOOP_SUBSCRIPTION);
                sub.onError(new IllegalStateException("Already subscribed"));
                return;
            }
            subscriber = sub;
            sub.onSubscribe(this);
            if (cancelled) {
                demand.release(1);
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                cancelled = true;
                demand.release(1);
                var sub = subscriber;
                if (sub != null) {
                    sub.onError(new IllegalArgumentException("Spec rule 3.9: n must be > 0"));
                }
                return;
            }
            int currentPermits = demand.availablePermits();
            int maxAdd = Integer.MAX_VALUE - currentPermits;
            if (maxAdd > 0) {
                int permits = n >= maxAdd ? maxAdd : (int) n;
                demand.release(permits);
            }
        }

        @Override
        public void cancel() {
            close();
        }

        private ByteBuf ensureBuffer() {
            var buf = current;
            if (buf == null) {
                buf = alloc.buffer(CHUNK_SIZE);
                current = buf;
            }
            return buf;
        }

        void flushCurrent() throws IOException {
            var buf = current;
            if (buf == null) {
                return;
            }
            current = null;
            if (!buf.isReadable()) {
                buf.release();
                return;
            }
            try {
                demand.acquire();
            } catch (InterruptedException e) {
                buf.release();
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while streaming response body");
            }
            if (cancelled) {
                buf.release();
                throw new IOException("Response stream cancelled by client");
            }
            var sub = Objects.requireNonNull(subscriber);
            sub.onNext(new KoraReadBuffer(buf));
        }

        void complete() {
            var sub = subscriber;
            if (sub != null && !cancelled) {
                sub.onComplete();
            }
        }

        void fail(Throwable t) {
            var sub = subscriber;
            if (sub != null && !cancelled) {
                sub.onError(t);
            }
        }

        @Override
        public void write(int b) throws IOException {
            if (cancelled) {
                throw new IOException("Response stream cancelled by client");
            }
            var buf = ensureBuffer();
            buf.writeByte(b);
            if (!buf.isWritable()) {
                flushCurrent();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            Objects.checkFromIndexSize(off, len, b.length);
            int remaining = len;
            int offset = off;
            while (remaining > 0) {
                if (cancelled) {
                    throw new IOException("Response stream cancelled by client");
                }
                var buf = ensureBuffer();
                int toWrite = Math.min(remaining, buf.writableBytes());
                buf.writeBytes(b, offset, toWrite);
                offset += toWrite;
                remaining -= toWrite;
                if (!buf.isWritable()) {
                    flushCurrent();
                }
            }
        }

        @Override
        public void flush() throws IOException {
            flushCurrent();
        }

        @Override
        public void close() {
            cancelled = true;
            demand.release(1);
            var buf = current;
            if (buf != null) {
                current = null;
                buf.release();
            }
        }
    }

    private static final class OptimisticBodyOutputStream extends OutputStream {
        private static final int BUFFER_SIZE = 8192;
        private static final int MAX_POOL_SIZE = 1024;
        private static final ConcurrentLinkedQueue<byte[]> BUFFER_POOL = new ConcurrentLinkedQueue<>();
        private static final AtomicInteger POOL_SIZE = new AtomicInteger();

        private final ByteBufAllocator streamAlloc;
        private final HttpResponse nettyResp;
        private final KoraOutboundAccess outboundAccess;

        private byte @Nullable [] buffer;
        private int count;
        @Nullable
        private BackpressuredOutputStream streaming;
        private boolean finished;

        OptimisticBodyOutputStream(ByteBufAllocator streamAlloc, HttpResponse nettyResp, KoraOutboundAccess outboundAccess) {
            this.streamAlloc = streamAlloc;
            this.nettyResp = nettyResp;
            this.outboundAccess = outboundAccess;
            buffer = borrowBuffer();
        }

        private static byte[] borrowBuffer() {
            var buf = BUFFER_POOL.poll();
            if (buf != null) {
                POOL_SIZE.decrementAndGet();
                return buf;
            }
            return new byte[BUFFER_SIZE];
        }

        private static void returnBuffer(byte[] buf) {
            if (buf.length == BUFFER_SIZE && POOL_SIZE.get() < MAX_POOL_SIZE) {
                POOL_SIZE.incrementAndGet();
                BUFFER_POOL.offer(buf);
            }
        }

        private BackpressuredOutputStream devolveToStreaming() throws IOException {
            var current = streaming;
            if (current != null) {
                return current;
            }
            var stream = new BackpressuredOutputStream(streamAlloc);
            streaming = stream;
            outboundAccess.write(nettyResp, stream);

            var buf = buffer;
            if (buf != null) {
                buffer = null;
                try {
                    if (count > 0) {
                        stream.write(buf, 0, count);
                    }
                } finally {
                    returnBuffer(buf);
                }
            }
            return stream;
        }

        @Override
        public void write(int b) throws IOException {
            var stream = streaming;
            if (stream != null) {
                stream.write(b);
                return;
            }
            var buf = Objects.requireNonNull(buffer);
            if (count + 1 > BUFFER_SIZE) {
                devolveToStreaming().write(b);
            } else {
                buf[count++] = (byte) b;
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) {
                return;
            }
            var stream = streaming;
            if (stream != null) {
                stream.write(b, off, len);
                return;
            }
            var buf = Objects.requireNonNull(buffer);
            if (count + len > BUFFER_SIZE) {
                devolveToStreaming().write(b, off, len);
            } else {
                System.arraycopy(b, off, buf, count, len);
                count += len;
            }
        }

        @Override
        public void flush() throws IOException {
            var stream = streaming;
            if (stream != null) {
                stream.flush();
            } else if (count > 0) {
                devolveToStreaming().flush();
            }
        }

        void finish() throws IOException {
            if (finished) {
                return;
            }
            finished = true;
            var stream = streaming;
            if (stream != null) {
                stream.flushCurrent();
                stream.complete();
                return;
            }
            var buf = Objects.requireNonNull(buffer);
            buffer = null;
            if (count == 0) {
                returnBuffer(buf);
                outboundAccess.write(nettyResp, (KoraAvailableBody) null);
            } else {
                var nettyBuf = new ReleasableHeapByteBuf(streamAlloc, buf, 0, count, () -> returnBuffer(buf));
                var availableBody = new KoraAvailableBody(new KoraReadBuffer(nettyBuf));
                outboundAccess.write(nettyResp, availableBody);
            }
        }

        @Override
        public void close() {
            var buf = buffer;
            if (buf != null) {
                buffer = null;
                returnBuffer(buf);
            }
            var stream = streaming;
            if (stream != null) {
                if (!finished) {
                    stream.fail(new IOException("Response stream closed before completion"));
                }
                stream.close();
            }
        }
    }

    private static final class NettyHttpBodySink implements HttpBodySink {
        private final ByteBufAllocator alloc;
        @Nullable
        ByteBuf buf;

        NettyHttpBodySink(ByteBufAllocator alloc) {
            this.alloc = alloc;
        }

        @Override
        public void acceptBytes(byte[] bytes, int offset, int length, @Nullable Runnable releaser) {
            releaseIfAllocated();
            if (length == 0) {
                if (releaser != null) {
                    releaser.run();
                }
                this.buf = Unpooled.EMPTY_BUFFER;
                return;
            }
            this.buf = new ReleasableHeapByteBuf(alloc, bytes, offset, length, releaser);
        }

        @Override
        public ByteBuffer allocateBuffer(int exactSize) {
            releaseIfAllocated();
            if (exactSize <= 4096) {
                var bytes = new byte[exactSize];
                this.buf = new ReleasableHeapByteBuf(alloc, bytes, 0, exactSize, null);
                return ByteBuffer.wrap(bytes);
            }
            ByteBuf direct = alloc.directBuffer(exactSize, exactSize);
            this.buf = direct;
            return direct.nioBuffer(0, exactSize);
        }

        @Override
        public void acceptAllocatedBuffer(ByteBuffer buffer) {
            ByteBuf current = Objects.requireNonNull(this.buf, "No buffer was allocated");
            current.writerIndex(buffer.remaining());
        }

        void releaseIfAllocated() {
            ByteBuf current = this.buf;
            if (current != null) {
                this.buf = null;
                current.release();
            }
        }
    }

    private static final class ReleasableHeapByteBuf extends UnpooledHeapByteBuf {
        private final @Nullable Runnable releaser;

        ReleasableHeapByteBuf(ByteBufAllocator alloc, byte[] array, int offset, int length, @Nullable Runnable releaser) {
            super(alloc, array, array.length);
            setIndex(offset, offset + length);
            this.releaser = releaser;
        }

        @Override
        protected void deallocate() {
            super.deallocate();
            if (releaser != null) {
                releaser.run();
            }
        }
    }

    public static final class RequestHeadersFactory implements HttpHeadersFactory {
        private final int initialPairs;

        private RequestHeadersFactory(int initialPairs) {
            this.initialPairs = initialPairs;
        }

        @Override
        public HttpHeaders newHeaders() {
            return new FlatHttpHeaders(initialPairs);
        }

        @Override
        public HttpHeaders newEmptyHeaders() {
            return EmptyHttpHeaders.INSTANCE;
        }
    }
}

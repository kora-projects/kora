package io.koraframework.http.server.netty.handler;

import io.koraframework.http.server.netty.body.*;
import io.koraframework.http.server.netty.buffer.*;
import io.koraframework.http.server.netty.compression.*;
import io.koraframework.http.server.netty.request.*;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.socket.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.*;
import org.jspecify.annotations.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;

public final class PipeliningServerHandler extends ChannelInboundHandlerAdapter implements ChannelFutureListener, Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(PipeliningServerHandler.class);

    static final int MAX_COMPOSITE_COMPONENTS = 4096;
    private static final long LINGER_IDLE_MILLIS = 5_000;

    final RequestHandler requestHandler;
    final KoraDroppingInboundHandler droppingInboundHandler = new KoraDroppingInboundHandler(this);
    final KoraDroppingInboundHandler noBodyInboundHandler = new KoraDroppingInboundHandler(this);
    private final InboundHandler baseInboundHandler = new KoraMessageInboundHandler(this);
    private final KoraOptimisticBufferingInboundHandler optimisticBufferingInboundHandler = new KoraOptimisticBufferingInboundHandler(this);

    @Nullable Compressor compressor;
    BodySizeLimits bodySizeLimits = BodySizeLimits.UNLIMITED;
    boolean requestDecompressionEnabled = true;
    boolean http10KeepAlive;
    boolean rejectUnsupportedHttpVersions;

    private InboundHandler inboundHandler = baseInboundHandler;
    final Deque<KoraOutboundAccess> outboundQueue = new ArrayDeque<>(1);
    @Nullable OutboundHandler outboundHandler = null;

    @Nullable
    private ChannelHandlerContext ctx;
    private boolean reading = false;
    private boolean readCalled = false;
    boolean removed = false;
    private boolean flushPending = false;
    private boolean writing = false;
    boolean shuttingDown = false;

    private long readIdleNanos;
    private long writeIdleNanos;
    private long lastReadNanos;
    private long lastWriteNanos;
    @Nullable
    private ScheduledFuture<?> idleFuture;

    private @Nullable KoraNettyBufferFactory bufferFactory;

    public PipeliningServerHandler(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
    }

    public ChannelHandlerContext requiredCtx() {
        return Objects.requireNonNull(ctx, "ctx");
    }

    public KoraNettyBufferFactory bufferFactory() {
        var factory = bufferFactory;
        if (factory == null) {
            factory = new KoraNettyBufferFactory(requiredCtx().alloc());
            bufferFactory = factory;
        }
        return factory;
    }

    public InboundHandler baseInboundHandler() {
        return baseInboundHandler;
    }

    public KoraOptimisticBufferingInboundHandler optimisticBufferingInboundHandler() {
        return optimisticBufferingInboundHandler;
    }

    public InboundHandler inboundHandler() {
        return inboundHandler;
    }

    public void setInboundHandler(InboundHandler inboundHandler) {
        this.inboundHandler = inboundHandler;
    }

    public void setOutboundHandler(@Nullable OutboundHandler outboundHandler) {
        this.outboundHandler = outboundHandler;
    }

    public void setCompressionStrategy(DefaultHttpCompressionStrategy compressionStrategy) {
        compressor = compressionStrategy.isEnabled() ? new Compressor(compressionStrategy) : null;
    }

    public void setBodySizeLimits(BodySizeLimits bodySizeLimits) {
        this.bodySizeLimits = bodySizeLimits;
    }

    public void setRequestDecompressionEnabled(boolean requestDecompressionEnabled) {
        this.requestDecompressionEnabled = requestDecompressionEnabled;
    }

    public void setHttp10KeepAlive(boolean http10KeepAlive) {
        this.http10KeepAlive = http10KeepAlive;
    }

    public void setRejectUnsupportedHttpVersions(boolean rejectUnsupportedHttpVersions) {
        this.rejectUnsupportedHttpVersions = rejectUnsupportedHttpVersions;
    }

    public boolean canHaveBody(HttpResponseStatus status) {
        int code = status.code();
        return !(code >= 100 && code < 200 || code == 204 || code == 304);
    }

    void accept(HttpRequest request, @Nullable AutoCloseable body, KoraOutboundAccess outboundAccess) {
        try {
            requestHandler.accept(requiredCtx(), request, body, outboundAccess);
        } catch (Exception e) {
            if (body != null) {
                try {
                    body.close();
                } catch (Exception closeError) {
                    e.addSuppressed(closeError);
                }
            }
            try {
                requestHandler.handleUnboundError(e);
            } catch (RuntimeException handlerError) {
                LOG.debug("Failed to handle an error from the request handler", handlerError);
            }
            rejectUnaccepted(outboundAccess);
        }
    }

    void rejectUnaccepted(KoraOutboundAccess outboundAccess) {
        if (outboundAccess.responseStarted()) {
            if (outboundQueue.contains(outboundAccess) || (outboundHandler != null && outboundHandler.outboundAccess == outboundAccess)) {
                outboundAccess.closeAfterWriteInEventLoop();
            } else {
                requiredCtx().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(CLOSE);
            }
            return;
        }
        try {
            outboundAccess.closeAfterWrite();
            var errResponse = new DefaultHttpResponse(outboundAccess.request().protocolVersion(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
            outboundAccess.write(errResponse, (KoraAvailableBody) null);
        } catch (RuntimeException e) {
            LOG.debug("Failed to write the error response for a request that was not accepted", e);
            if (!outboundAccess.responseStarted()) {
                requestHandler.responseWritten(outboundAccess.attachment());
            }
            requiredCtx().close();
        }
    }

    boolean hasBody(HttpRequest request) {
        if (request.decoderResult().isFailure()) {
            return false;
        }
        if (request instanceof KoraHttpRequest koraReq) {
            long cl = koraReq.knownContentLength();
            if (cl >= 0) {
                return cl > 0;
            }
            return false;
        }
        var headers = request.headers();
        var contentLengthValue = headers.get(HttpHeaderNames.CONTENT_LENGTH);
        if (contentLengthValue != null) {
            int len = contentLengthValue.length();
            if (len == 1 && contentLengthValue.charAt(0) == '0') {
                return false;
            }
            if (len > 0) {
                boolean allZeros = true;
                boolean validDigits = true;
                for (int i = 0; i < len; i++) {
                    char c = contentLengthValue.charAt(i);
                    if (c < '0' || c > '9') {
                        validDigits = false;
                        break;
                    }
                    if (c != '0') {
                        allZeros = false;
                    }
                }
                if (validDigits) {
                    return !allZeros;
                }
            }
            return HttpUtil.isTransferEncodingChunked(request);
        }
        return headers.contains(HttpHeaderNames.TRANSFER_ENCODING) && HttpUtil.isTransferEncodingChunked(request);
    }

    void refreshNeedMore() {
        assert ctx != null;
        if (!readCalled && outboundQueue.size() <= 1 && inboundHandler.needMore()) {
            readCalled = true;
            requiredCtx().read();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cancelIdleTimers();
        if (outboundHandler != null) {
            outboundHandler.outboundAccess.onChannelInactive();
        }
        for (var queued : outboundQueue) {
            queued.onChannelInactive();
        }
        super.channelInactive(ctx);
    }


    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        ctx.channel().config().setAutoRead(false);
        long now = System.nanoTime();
        lastReadNanos = now;
        lastWriteNanos = now;
        scheduleNextIdleCheck(ctx, now);
        refreshNeedMore();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        removed = true;
        cancelIdleTimers();
        if (outboundHandler != null) {
            outboundHandler.discardOutbound();
        }
        for (var queued : outboundQueue) {
            if (queued.handler() != null) {
                queued.handler().discardOutbound();
            }
        }
        inboundHandler.discard();
        outboundQueue.clear();
        requestHandler.removed();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        reading = true;
        if (readIdleNanos > 0) {
            lastReadNanos = System.nanoTime();
        }
        inboundHandler.read(msg);
    }

    void closeAfterContentDecoderFailure() {
        var lastResponse = outboundQueue.peekLast();
        if (lastResponse == null && outboundHandler != null) {
            lastResponse = outboundHandler.outboundAccess;
        }
        if (lastResponse == null) {
            requiredCtx().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(CLOSE);
        } else {
            lastResponse.closeAfterWriteInEventLoop();
        }
    }

    void handleContentDecoderFailure(HttpContent content) {
        var cause = content.decoderResult().cause();
        content.release();
        closeAfterContentDecoderFailure();
        inboundHandler.handleUpstreamError(cause);
        inboundHandler.read(LastHttpContent.EMPTY_LAST_CONTENT);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        inboundHandler.readComplete();
        reading = false;
        readCalled = false;
        if (flushPending) {
            ctx.flush();
            flushPending = false;
        }
        refreshNeedMore();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        inboundHandler.handleUpstreamError(cause);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        writeSome();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleStateEvent) {
            var state = idleStateEvent.state();
            if (state == IdleState.ALL_IDLE
                || (state == IdleState.READER_IDLE && readCalled)
                || (state == IdleState.WRITER_IDLE && (outboundHandler != null || !outboundQueue.isEmpty()))) {
                ctx.close();
                return;
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    public ChannelFuture write0(Object message, boolean flush, boolean close, boolean needsPromise, @Nullable ChannelPromise given) {
        if (writeIdleNanos > 0) {
            lastWriteNanos = System.nanoTime();
        }
        var context = requiredCtx();
        if (close) {
            var promise = given == null ? context.newPromise() : given;
            promise.addListener(this);
            if (reading && !flush) {
                return context.write(message, promise);
            }
            return context.writeAndFlush(message, promise);
        }
        ChannelPromise promise;
        if (given != null) {
            promise = given.addListener(FIRE_EXCEPTION_ON_FAILURE);
        } else {
            promise = needsPromise
                ? context.newPromise().addListener(FIRE_EXCEPTION_ON_FAILURE)
                : context.voidPromise();
        }
        if (flush) {
            if (reading) {
                flushPending = true;
                return context.write(message, promise);
            }
            return context.writeAndFlush(message, promise);
        }
        return context.write(message, promise);
    }

    @Override
    public void operationComplete(ChannelFuture future) {
        if (future.isSuccess()) {
            closeAfterResponse();
        } else {
            var cause = future.cause();
            if (cause != null && ctx != null) {
                ctx.fireExceptionCaught(cause);
            }
            future.channel().close();
        }
    }

    private void scheduleNextIdleCheck(ChannelHandlerContext context, long now) {
        long delayNanos = computeNextIdleDelayNanos(now);
        if (delayNanos > 0) {
            idleFuture = context.executor().schedule(this, delayNanos, TimeUnit.NANOSECONDS);
        }
    }

    private long computeNextIdleDelayNanos(long now) {
        long nextDelay = Long.MAX_VALUE;
        if (readIdleNanos > 0) {
            nextDelay = Math.clamp(readIdleNanos - (now - lastReadNanos), 1L, nextDelay);
        }
        if (writeIdleNanos > 0) {
            nextDelay = Math.clamp(writeIdleNanos - (now - lastWriteNanos), 1L, nextDelay);
        }
        return nextDelay == Long.MAX_VALUE ? 0L : nextDelay;
    }

    @Override
    public void run() {
        var context = ctx;
        if (context == null || !context.channel().isOpen()) {
            return;
        }
        long now = System.nanoTime();
        if (readIdleNanos > 0 && readCalled && (now - lastReadNanos) >= readIdleNanos) {
            context.close();
            return;
        }
        if (writeIdleNanos > 0 && (outboundHandler != null || !outboundQueue.isEmpty()) && (now - lastWriteNanos) >= writeIdleNanos) {
            context.close();
            return;
        }
        scheduleNextIdleCheck(context, now);
    }

    private void cancelIdleTimers() {
        var future = idleFuture;
        if (future != null) {
            idleFuture = null;
            future.cancel(false);
        }
    }

    void writeSome() {
        var context = ctx;
        assert context != null;
        if (writing) {
            return;
        }
        writing = true;
        try {
            while (context.channel().isWritable()) {
                if (outboundHandler == null) {
                    var next = outboundQueue.peek();
                    if (next != null && next.handler() != null) {
                        outboundQueue.poll();
                        outboundHandler = next.handler();
                        refreshNeedMore();
                    } else {
                        return;
                    }
                }
                var oldHandler = outboundHandler;
                oldHandler.writeSome();
                if (outboundHandler == oldHandler) {
                    break;
                }
                if (outboundHandler == null && outboundQueue.isEmpty()) {
                    break;
                }
            }
        } finally {
            writing = false;
        }
    }

    @SuppressWarnings("resource")
    public CompletionStage<?> shutdownGracefully() {
        assert ctx != null;
        var context = requiredCtx();
        if (context.executor().inEventLoop()) {
            shutdownGracefully0();
        } else {
            context.executor().execute(new ShutdownGracefullyTask());
        }
        var future = new CompletableFuture<Void>();
        context.channel().closeFuture().addListener(new CompleteFutureListener(future));
        return future;
    }

    private void shutdownGracefully0() {
        assert ctx != null;
        shuttingDown = true;
        if (inboundHandler == baseInboundHandler && outboundHandler == null && outboundQueue.isEmpty()) {
            requiredCtx().close();
        } else {
            var lastResponse = outboundQueue.peekLast();
            if (lastResponse == null && outboundHandler != null) {
                lastResponse = outboundHandler.outboundAccess;
            }
            if (lastResponse != null) {
                lastResponse.closeAfterWriteInEventLoop();
            }
        }
    }

    static ByteBuf composeBody(ByteBufAllocator alloc, List<ByteBuf> pieces) {
        var composite = alloc.compositeBuffer(MAX_COMPOSITE_COMPONENTS);
        try (var owned = new OwnedBuffer(composite)) {
            composite.addComponents(true, pieces);
            return owned.take();
        }
    }

    private void closeAfterResponse() {
        var context = requiredCtx();
        if (inboundHandler == baseInboundHandler || inboundHandler == noBodyInboundHandler) {
            inboundHandler = new KoraClosedInboundHandler();
            context.close();
            return;
        }
        if (removed || !(context.channel() instanceof DuplexChannel channel) || !channel.isActive()) {
            context.close();
            return;
        }
        inboundHandler.discard();
        var lingering = new KoraLingeringInboundHandler(context);
        inboundHandler = lingering;
        lingering.scheduleTimeout(LINGER_IDLE_MILLIS);
        channel.closeFuture().addListener(new CancelLingerTimeoutListener(lingering));

        channel.shutdownOutput();
        refreshNeedMore();
    }

    public void setCompressor(@Nullable Compressor compressor) {
        this.compressor = compressor;
    }

    public void setIdleTimeouts(long readIdleMillis, long writeIdleMillis) {
        this.readIdleNanos = readIdleMillis > 0 ? TimeUnit.MILLISECONDS.toNanos(readIdleMillis) : 0L;
        this.writeIdleNanos = writeIdleMillis > 0 ? TimeUnit.MILLISECONDS.toNanos(writeIdleMillis) : 0L;
    }

    private static final class CancelLingerTimeoutListener implements ChannelFutureListener {
        private final KoraLingeringInboundHandler lingering;

        private CancelLingerTimeoutListener(KoraLingeringInboundHandler lingering) {
            this.lingering = lingering;
        }

        @Override
        public void operationComplete(ChannelFuture future) {
            lingering.cancelTimeout();
        }
    }

    private final class ShutdownGracefullyTask implements Runnable {
        @Override
        public void run() {
            shutdownGracefully0();
        }
    }

    private static final class CompleteFutureListener implements ChannelFutureListener {
        private final CompletableFuture<Void> future;

        private CompleteFutureListener(CompletableFuture<Void> future) {
            this.future = future;
        }

        @Override
        public void operationComplete(ChannelFuture channelFuture) {
            future.complete(null);
        }
    }
}

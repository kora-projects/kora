package io.koraframework.http.server.netty.handler;

import io.koraframework.http.server.netty.body.*;
import io.koraframework.http.server.netty.buffer.*;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.jspecify.annotations.*;
import org.slf4j.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class KoraOutboundAccess implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(KoraOutboundAccess.class);

    private static final String DUPLICATE_RESPONSE_MSG = "Only one response per request: a response written from another thread is dropped";
    private static final AtomicReferenceFieldUpdater<KoraOutboundAccess, FullHttpResponse> PENDING_RESPONSE_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(KoraOutboundAccess.class, FullHttpResponse.class, "pendingFullResponse");

    private final HttpRequest request;
    private final ChannelHandlerContext ctx;
    private final PipeliningServerHandler parent;

    private boolean skipCompression;
    @Nullable
    private OutboundHandler handler;
    @Nullable
    private Object attachment;
    private boolean closeAfterWrite;
    private volatile boolean finalWriteIssued;
    @Nullable
    private volatile FullHttpResponse pendingFullResponse;
    @Nullable
    private volatile Thread workerThread;

    public KoraOutboundAccess(HttpRequest request, ChannelHandlerContext ctx, PipeliningServerHandler parent) {
        this.request = request;
        this.ctx = ctx;
        this.parent = parent;
    }

    public void attachWorkerThread(Thread thread) {
        this.workerThread = thread;
        if (!ctx.channel().isActive() && finalWritePending()) {
            thread.interrupt();
        }
    }

    public void detachWorkerThread() {
        this.workerThread = null;
    }

    public void onChannelInactive() {
        var t = workerThread;
        if (t != null && finalWritePending()) {
            t.interrupt();
        }
    }

    public HttpRequest request() {
        return request;
    }

    public void attachment(Object attachment) {
        this.attachment = attachment;
    }

    @Nullable
    public Object attachment() {
        return attachment;
    }

    private boolean finalWritePending() {
        return !finalWriteIssued;
    }

    public void setFinalWriteIssued(boolean finalWriteIssued) {
        this.finalWriteIssued = finalWriteIssued;
    }

    public void closeAfterWrite() {
        closeAfterWrite = true;
    }

    public void closeAfterWriteInEventLoop() {
        closeAfterWrite = true;
        var current = handler;
        if (current != null) {
            current.markCloseAfterWrite();
        }
    }

    private void preprocess(HttpResponse message, boolean knownFixedOrEmptyBody) {
        var requestVersion = request.protocolVersion();
        var responseVersion = parent.rejectUnsupportedHttpVersions && !HttpVersion.HTTP_1_0.equals(requestVersion)
            ? HttpVersion.HTTP_1_1 : requestVersion;

        if (!message.protocolVersion().equals(responseVersion)) {
            message.setProtocolVersion(responseVersion);
        }

        var reqHeaders = request.headers();
        if (requestVersion.isKeepAliveDefault()) {
            if (reqHeaders.contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)) {
                closeAfterWrite();
            }
        } else if (!requestsKeepAlive()) {
            closeAfterWrite();
        }

        var respHeaders = message.headers();
        boolean http10KeptAlive = false;
        if (responseVersion.isKeepAliveDefault()) {
            if (respHeaders.contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true)) {
                closeAfterWrite();
            } else if (closeAfterWrite) {
                respHeaders.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            }
        } else {
            boolean hasRespConnection = respHeaders.contains(HttpHeaderNames.CONNECTION);
            if (!hasRespConnection || !respHeaders.contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true)) {
                if (parent.http10KeepAlive && !closeAfterWrite
                    && (knownFixedOrEmptyBody || HttpUtil.isContentLengthSet(message) || !parent.canHaveBody(message.status()) || HttpMethod.HEAD.equals(request.method()))
                    && (!hasRespConnection || !respHeaders.contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true))) {
                    http10KeptAlive = true;
                    respHeaders.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                } else {
                    closeAfterWrite();
                }
            } else if (closeAfterWrite) {
                respHeaders.remove(HttpHeaderNames.CONNECTION);
            }
        }

        if (!knownFixedOrEmptyBody
            && !http10KeptAlive
            && !HttpMethod.HEAD.equals(request.method())
            && !HttpUtil.isContentLengthSet(message)
            && !HttpUtil.isTransferEncodingChunked(message)
            && parent.canHaveBody(message.status())) {
            HttpUtil.setKeepAlive(message, false);
            closeAfterWrite();
        }
    }

    private boolean requestsKeepAlive() {
        if (parent.http10KeepAlive) {
            var headers = request.headers();
            return headers.containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true)
                && !headers.containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE, true);
        }
        return request.headers().contains(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE, true);
    }

    public void writeContinue() {
        if (handler == null) {
            write(new ContinueOutboundHandler(this, parent));
        }
    }

    public boolean responseStarted() {
        var current = handler;
        return current != null && !(current instanceof ContinueOutboundHandler cont && cont.next == null);
    }

    public void write(OutboundHandler handler) {
        if (responseStarted()) {
            handler.discardRefused();
            throw new IllegalStateException("Only one response per request");
        }

        var eventLoop = ctx.channel().eventLoop();
        if (!eventLoop.inEventLoop()) {
            try {
                eventLoop.execute(new WriteMovedTask(handler));
            } catch (Throwable t) {
                handler.discardRefused();
                throw t;
            }
            return;
        }

        writeInEventLoop(handler);
    }

    private final class WriteMovedTask implements Runnable {
        private final OutboundHandler handler;

        private WriteMovedTask(OutboundHandler handler) {
            this.handler = handler;
        }

        @Override
        public void run() {
            writeMoved(handler);
        }
    }

    private void writeMoved(OutboundHandler handler) {
        if (responseStarted()) {
            LOG.warn(DUPLICATE_RESPONSE_MSG);
            handler.discardRefused();
            return;
        }
        try {
            write(handler);
        } catch (Throwable t) {
            giveUpOffLoopWrite(t);
        }
    }

    public void abort() {
        var eventLoop = ctx.channel().eventLoop();
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(new AbortTask());
            return;
        }
        var current = handler instanceof ContinueOutboundHandler cont ? cont.next : handler;
        if (parent.removed || (current != null && current.responseWritten)) {
            return;
        }
        ctx.close();
    }

    private final class AbortTask implements Runnable {
        @Override
        public void run() {
            abort();
        }
    }

    public void runInResponseOrder(Runnable action, Runnable discard) {
        write(new ActionOutboundHandler(this, action, discard, parent));
    }

    public void writeHeadResponse(HttpResponse response) {
        writeFull(new DefaultFullHttpResponse(
            response.protocolVersion(),
            response.status(),
            Unpooled.EMPTY_BUFFER,
            response.headers(),
            EmptyHttpHeaders.INSTANCE
        ), true);
    }

    private void writeFull(FullHttpResponse response, boolean headResponse) {
        FullOutboundHandler oh = null;
        try {
            var headers = response.headers();
            headers.remove(HttpHeaderNames.TRANSFER_ENCODING);
            int readableBytes = response.content().readableBytes();
            if (parent.canHaveBody(response.status())) {
                if (!headResponse) {
                    if (readableBytes == 0) {
                        headers.set(HttpHeaderNames.CONTENT_LENGTH, HttpHeaderValues.ZERO);
                    } else {
                        headers.setInt(HttpHeaderNames.CONTENT_LENGTH, readableBytes);
                    }
                }
            } else {
                headers.remove(HttpHeaderNames.CONTENT_LENGTH);
            }
            preprocess(response, true);
            oh = new FullOutboundHandler(this, response, parent);
            if (parent.compressor != null && !skipCompression && readableBytes > 0) {
                prepareCompression(response, oh, readableBytes);
            }
        } catch (Throwable t) {
            response.release();
            discardCompression(oh);
            throw t;
        }
        writeInEventLoop(oh);
    }

    private void writeInEventLoop(OutboundHandler handler) {
        if (parent.removed) {
            handler.discardOutbound();
            return;
        }

        if (closeAfterWrite) {
            boolean marked = false;
            try {
                handler.markCloseAfterWrite();
                marked = true;
            } finally {
                if (!marked) {
                    handler.discardRefused();
                }
            }
        }

        if (this.handler instanceof ContinueOutboundHandler cont) {
            cont.next = handler;
            parent.writeSome();
        } else {
            this.handler = handler;
            if (parent.outboundQueue.peek() == this) {
                parent.writeSome();
            }
        }
    }

    private static void discardCompression(@Nullable OutboundHandler handler) {
        if (handler != null && handler.compressionSession != null) {
            handler.compressionSession.discard();
            handler.compressionSession = null;
        }
    }

    public void write(HttpResponse response, @Nullable KoraAvailableBody body) {
        if (body == null) {
            if (response instanceof FullHttpResponse full && full.content().readableBytes() == 0) {
                write(full);
                return;
            }
            write(new DefaultFullHttpResponse(
                response.protocolVersion(),
                response.status(),
                Unpooled.EMPTY_BUFFER,
                response.headers(),
                EmptyHttpHeaders.INSTANCE
            ));
            return;
        }
        try {
            write(new DefaultFullHttpResponse(
                response.protocolVersion(),
                response.status(),
                body.toByteBuf(),
                response.headers(),
                EmptyHttpHeaders.INSTANCE
            ));
        } catch (Throwable t) {
            body.close();
            throw t;
        }
    }

    public void write(HttpResponse response, Flow.Publisher<KoraReadBuffer> body) {
        var eventLoop = ctx.channel().eventLoop();
        if (!eventLoop.inEventLoop()) {
            try {
                eventLoop.execute(new WriteClaimedStreamingTask(response, body));
            } catch (Throwable t) {
                closeBodyQuietly(body);
                throw t;
            }
            return;
        }

        response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
        if (parent.canHaveBody(response.status()) && !request.protocolVersion().equals(HttpVersion.HTTP_1_0)) {
            response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        } else {
            response.headers().remove(HttpHeaderNames.TRANSFER_ENCODING);
        }

        preprocess(response, false);
        var oh = new KoraStreamingOutboundHandler(this, response, parent);
        try {
            prepareCompression(response, oh, -1);
            body.subscribe(oh);
        } catch (Throwable t) {
            discardCompression(oh);
            closeBodyQuietly(body);
            throw t;
        }
        write(oh);
    }

    private final class WriteClaimedStreamingTask implements Runnable {
        private final HttpResponse response;
        private final Flow.Publisher<KoraReadBuffer> body;

        private WriteClaimedStreamingTask(HttpResponse response, Flow.Publisher<KoraReadBuffer> body) {
            this.response = response;
            this.body = body;
        }

        @Override
        public void run() {
            writeClaimedStreaming(response, body);
        }
    }

    private void writeClaimedStreaming(HttpResponse response, Flow.Publisher<KoraReadBuffer> body) {
        if (responseStarted()) {
            LOG.warn(DUPLICATE_RESPONSE_MSG);
            closeBodyQuietly(body);
            return;
        }
        try {
            write(response, body);
        } catch (Throwable t) {
            closeBodyQuietly(body);
            giveUpOffLoopWrite(t);
        }
    }

    private static void closeBodyQuietly(Flow.Publisher<KoraReadBuffer> body) {
        if (body instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void giveUpOffLoopWrite(Throwable t) {
        var current = handler instanceof ContinueOutboundHandler cont ? cont.next : handler;
        if (current != null && current.responseWritten) {
            LOG.warn("Failed to write a response that followed a response written from another thread: {}", t.getMessage(), t);
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        LOG.warn("Failed to write the response: {}", t.getMessage(), t);
        parent.requestHandler.responseFailed(attachment, t);
        if (current != null) {
            if (parent.outboundHandler != handler && parent.outboundHandler != current && !parent.outboundQueue.contains(this)) {
                current.discardOutbound();
            }
            ctx.close();
        } else {
            parent.rejectUnaccepted(this);
        }
    }

    public void write(FullHttpResponse response) {
        var eventLoop = ctx.channel().eventLoop();
        if (!eventLoop.inEventLoop()) {
            if (!PENDING_RESPONSE_UPDATER.compareAndSet(this, null, response)) {
                LOG.warn(DUPLICATE_RESPONSE_MSG);
                response.release();
                return;
            }
            try {
                eventLoop.execute(this);
            } catch (Throwable t) {
                if (PENDING_RESPONSE_UPDATER.compareAndSet(this, response, null)) {
                    response.release();
                }
                throw t;
            }
            return;
        }
        writeFull(response, false);
    }

    @Override
    public void run() {
        var response = PENDING_RESPONSE_UPDATER.getAndSet(this, null);
        if (response == null) {
            return;
        }
        if (responseStarted()) {
            LOG.warn(DUPLICATE_RESPONSE_MSG);
            response.release();
            return;
        }
        try {
            writeFull(response, false);
        } catch (Throwable t) {
            response.release();
            giveUpOffLoopWrite(t);
        }
    }

    public void skipCompression() {
        skipCompression = true;
    }

    private void prepareCompression(HttpResponse response, OutboundHandler outboundHandler, long contentLength) {
        if (parent.compressor == null || skipCompression) {
            return;
        }
        var compressionSession = parent.compressor.prepare(ctx, request, response, contentLength);
        if (compressionSession != null) {
            outboundHandler.compressionSession = compressionSession;
            if (!(response instanceof FullHttpResponse) && response.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
                response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
            }
        }
    }

    @Nullable
    public OutboundHandler handler() {
        return handler;
    }

    public void setHandler(@Nullable OutboundHandler handler) {
        this.handler = handler;
    }

    public boolean isCloseAfterWrite() {
        return closeAfterWrite;
    }
}

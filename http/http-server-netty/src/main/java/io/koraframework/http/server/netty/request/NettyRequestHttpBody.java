package io.koraframework.http.server.netty.request;

import io.koraframework.http.common.body.*;
import io.koraframework.http.server.netty.body.*;
import io.koraframework.http.server.netty.buffer.*;
import io.netty.buffer.*;
import io.netty.handler.codec.http.*;
import org.jspecify.annotations.*;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

public final class NettyRequestHttpBody implements HttpBodyInput {

    private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);

    private final HttpRequest nettyRequest;
    @Nullable
    private final AutoCloseable rawBody;
    @Nullable
    private InputStream inputStream;
    @Nullable
    private ByteBuffer cachedFullContent;
    @Nullable
    private ByteBuf retainedByteBuf;
    private boolean consumed;
    private final int directToHeapThreshold;

    public NettyRequestHttpBody(HttpRequest nettyRequest, @Nullable AutoCloseable rawBody, int directToHeapThreshold) {
        this.nettyRequest = Objects.requireNonNull(nettyRequest, "nettyRequest");
        this.rawBody = rawBody;
        this.directToHeapThreshold = directToHeapThreshold;
    }

    @Override
    public long contentLength() {
        if (rawBody instanceof KoraAvailableBody available) {
            return available.length();
        }
        return HttpUtil.getContentLength(nettyRequest, -1L);
    }

    @Nullable
    @Override
    public String contentType() {
        return nettyRequest.headers().get(HttpHeaderNames.CONTENT_TYPE);
    }

    @Nullable
    @Override
    public ByteBuffer getFullContentIfAvailable() {
        if (cachedFullContent != null) {
            return cachedFullContent.duplicate();
        }
        if (rawBody == null) {
            consumed = true;
            cachedFullContent = EMPTY_BYTE_BUFFER;
            return EMPTY_BYTE_BUFFER;
        }
        if (rawBody instanceof KoraAvailableBody available && !consumed) {
            consumed = true;
            int len = (int) available.length();
            if (len == 0) {
                available.close();
                cachedFullContent = EMPTY_BYTE_BUFFER;
                return EMPTY_BYTE_BUFFER;
            }
            var buf = available.toByteBuf();
            ByteBuffer nioBuf;
            if (buf.hasArray()) {
                nioBuf = ByteBuffer.wrap(buf.array(), buf.arrayOffset() + buf.readerIndex(), len);
                retainedByteBuf = buf;
            } else if (len <= directToHeapThreshold) {
                var bytes = new byte[len];
                buf.getBytes(buf.readerIndex(), bytes);
                buf.release();
                nioBuf = ByteBuffer.wrap(bytes);
            } else {
                nioBuf = buf.nioBuffer();
                retainedByteBuf = buf;
            }
            cachedFullContent = nioBuf;
            return nioBuf;
        }
        return null;
    }

    @Override
    public ReadResult readAllBytes(byte[] initialBuffer) throws IOException {
        if (rawBody == null) {
            consumed = true;
            return new ReadResult(initialBuffer, 0);
        }
        if (rawBody instanceof KoraAvailableBody available && !consumed) {
            consumed = true;
            int len = (int) available.length();
            if (len == 0) {
                return new ReadResult(initialBuffer, 0);
            }
            var target = len <= initialBuffer.length ? initialBuffer : new byte[len];
            var buf = available.toByteBuf();
            try {
                buf.getBytes(buf.readerIndex(), target, 0, len);
                return new ReadResult(target, len);
            } finally {
                buf.release();
            }
        }
        return HttpBodyInput.super.readAllBytes(initialBuffer);
    }

    @Override
    public InputStream asInputStream() {
        var is = inputStream;
        if (is != null) {
            return is;
        }
        consumed = true;
        var body = rawBody;
        if (body == null) {
            is = InputStream.nullInputStream();
        } else if (body instanceof KoraAvailableBody available) {
            is = available.toInputStream();
        } else if (body instanceof KoraStreamingNettyBody streaming) {
            is = new FlowInputStream(streaming);
        } else {
            throw new IllegalArgumentException("Unsupported body type: " + body.getClass());
        }
        inputStream = is;
        return is;
    }

    @Override
    public void close() {
        if (retainedByteBuf != null) {
            retainedByteBuf.release();
            retainedByteBuf = null;
        }
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (Exception ignored) {
            }
        }
        if (rawBody != null) {
            try {
                rawBody.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static final class FlowInputStream extends InputStream implements Flow.Subscriber<KoraReadBuffer> {
        private static final Object COMPLETE_MARKER = new Object();

        private final KoraStreamingNettyBody publisher;
        private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        private volatile Flow.@Nullable Subscription subscription;
        @Nullable
        private KoraReadBuffer currentBuffer;
        @Nullable
        private InputStream currentStream;
        private boolean finished;
        private volatile boolean closed;

        FlowInputStream(KoraStreamingNettyBody publisher) {
            this.publisher = publisher;
            publisher.subscribe(this);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (closed) {
                subscription.cancel();
            } else {
                subscription.request(1);
            }
        }

        @Override
        public void onNext(KoraReadBuffer item) {
            if (closed) {
                item.close();
                return;
            }
            queue.offer(item);
        }

        @Override
        public void onError(Throwable throwable) {
            queue.offer(throwable);
        }

        @Override
        public void onComplete() {
            queue.offer(COMPLETE_MARKER);
        }

        private boolean ensureCurrentStream() throws IOException {
            if (finished) {
                return false;
            }
            if (currentStream != null) {
                return true;
            }
            Object next;
            try {
                next = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                close();
                throw new InterruptedIOException("Interrupted while reading request body");
            }

            if (next == COMPLETE_MARKER) {
                finished = true;
                return false;
            }
            if (next instanceof Throwable t) {
                finished = true;
                throw t instanceof IOException io ? io : new IOException("Request body stream failed", t);
            }

            var buf = (KoraReadBuffer) next;
            currentBuffer = buf;
            currentStream = buf.toInputStream();
            return true;
        }

        private void releaseCurrentAndRequestMore() {
            if (currentStream != null) {
                try {
                    currentStream.close();
                } catch (IOException ignored) {
                }
                currentStream = null;
            }
            if (currentBuffer != null) {
                currentBuffer.close();
                currentBuffer = null;
            }
            var sub = subscription;
            if (sub != null && !closed && !finished) {
                sub.request(1);
            }
        }

        @Override
        public int read() throws IOException {
            while (ensureCurrentStream()) {
                int b = Objects.requireNonNull(currentStream).read();
                if (b != -1) {
                    return b;
                }
                releaseCurrentAndRequestMore();
            }
            return -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) {
                return 0;
            }
            while (ensureCurrentStream()) {
                int read = Objects.requireNonNull(currentStream).read(b, off, len);
                if (read != -1) {
                    return read;
                }
                releaseCurrentAndRequestMore();
            }
            return -1;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            var sub = subscription;
            if (sub != null) {
                sub.cancel();
            }
            releaseCurrentAndRequestMore();
            Object item;
            while ((item = queue.poll()) != null) {
                if (item instanceof KoraReadBuffer rb) {
                    rb.close();
                }
            }
            publisher.close();
        }
    }
}

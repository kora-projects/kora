package io.koraframework.http.server.netty.request;

import io.koraframework.http.server.netty.buffer.*;
import io.netty.buffer.*;
import io.netty.handler.codec.*;
import io.netty.handler.codec.http.*;
import org.jspecify.annotations.*;

public final class BytesFullHttpResponse implements FullHttpResponse {
    private final HttpVersion version;
    private final HttpResponseStatus status;
    private final HttpHeaders headers;
    private final byte[] array;
    private final int offset;
    private final int length;
    private final @Nullable Runnable releaser;

    private @Nullable ByteBuf lazyContent;
    private boolean released;

    public BytesFullHttpResponse(
        HttpVersion version,
        HttpResponseStatus status,
        HttpHeaders headers,
        byte[] array,
        int offset,
        int length,
        @Nullable Runnable releaser
    ) {
        this.version = version;
        this.status = status;
        this.headers = headers;
        this.array = array;
        this.offset = offset;
        this.length = length;
        this.releaser = releaser;
    }

    public byte[] array() {
        return array;
    }

    public int offset() {
        return offset;
    }

    public int length() {
        return length;
    }

    @Override
    public ByteBuf content() {
        ByteBuf buf = lazyContent;
        if (buf == null) {
            buf = length == 0
                ? Unpooled.EMPTY_BUFFER
                : new ReleasableHeapByteBuf(UnpooledByteBufAllocator.DEFAULT, array, offset, length, releaser);
            lazyContent = buf;
        }
        return buf;
    }

    @Override
    public int refCnt() {
        ByteBuf buf = lazyContent;
        return buf != null ? buf.refCnt() : (released ? 0 : 1);
    }

    @Override
    public FullHttpResponse retain() {
        content().retain();
        return this;
    }

    @Override
    public FullHttpResponse retain(int increment) {
        content().retain(increment);
        return this;
    }

    @Override
    public FullHttpResponse touch() {
        return this;
    }

    @Override
    public FullHttpResponse touch(Object hint) {
        return this;
    }

    @Override
    public boolean release() {
        ByteBuf buf = lazyContent;
        if (buf != null) {
            return buf.release();
        }
        if (!released) {
            released = true;
            if (releaser != null) {
                releaser.run();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean release(int decrement) {
        ByteBuf buf = lazyContent;
        if (buf != null) {
            return buf.release(decrement);
        }
        return release();
    }

    @Override
    public HttpHeaders trailingHeaders() {
        return EmptyHttpHeaders.INSTANCE;
    }

    @Override
    public HttpVersion protocolVersion() {
        return version;
    }

    @Override
    public FullHttpResponse setProtocolVersion(HttpVersion version) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public HttpResponseStatus getStatus() {
        return status;
    }

    @Override
    @Deprecated
    public HttpVersion getProtocolVersion() {
        return version;
    }

    @Override
    @Deprecated
    public DecoderResult getDecoderResult() {
        return DecoderResult.SUCCESS;
    }

    @Override
    public HttpResponseStatus status() {
        return status;
    }

    @Override
    public FullHttpResponse setStatus(HttpResponseStatus status) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public DecoderResult decoderResult() {
        return DecoderResult.SUCCESS;
    }

    @Override
    public void setDecoderResult(DecoderResult result) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FullHttpResponse copy() {
        return replace(content().copy());
    }

    @Override
    public FullHttpResponse duplicate() {
        return replace(content().duplicate());
    }

    @Override
    public FullHttpResponse retainedDuplicate() {
        return replace(content().retainedDuplicate());
    }

    @Override
    public FullHttpResponse replace(ByteBuf content) {
        return new DefaultFullHttpResponse(version, status, content, headers, EmptyHttpHeaders.INSTANCE);
    }
}

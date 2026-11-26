package io.koraframework.http.server.netty.handler;

import io.koraframework.http.server.netty.body.*;
import io.netty.buffer.*;
import io.netty.handler.codec.http.*;
import org.jspecify.annotations.*;

import java.util.*;

public final class KoraOptimisticBufferingInboundHandler extends InboundHandler {
    private final PipeliningServerHandler parent;
    @Nullable
    private HttpContent singleContent;
    @Nullable
    private List<HttpContent> buffer;

    @Nullable
    private HttpRequest request;
    @Nullable
    private KoraOutboundAccess outboundAccess;
    private long receivedLength;
    private boolean failed;

    public KoraOptimisticBufferingInboundHandler(PipeliningServerHandler parent) {
        this.parent = parent;
    }

    public void init(HttpRequest request, KoraOutboundAccess outboundAccess) {
        assert singleContent == null;
        assert buffer == null || buffer.isEmpty();
        assert receivedLength == 0;
        assert !failed;
        this.request = request;
        this.outboundAccess = outboundAccess;
    }

    @Override
    void read(Object message) {
        var content = (HttpContent) message;
        boolean last = message.getClass() == DefaultLastHttpContent.class || message instanceof LastHttpContent;
        if (last && content.decoderResult().isFailure()) {
            parent.handleContentDecoderFailure(content);
            return;
        }
        var buf = content.content();
        int readableBytes = buf.readableBytes();
        if (readableBytes > 0) {
            if (failed) {
                content.release();
                if (last) {
                    resetAndRestoreBase();
                }
                return;
            }
            long maxBodySize = parent.bodySizeLimits.maxBodySize();
            if (maxBodySize != Long.MAX_VALUE && readableBytes > maxBodySize - receivedLength) {
                long actualLength = receivedLength > Long.MAX_VALUE - readableBytes ? Long.MAX_VALUE : receivedLength + readableBytes;
                failed = true;
                content.release();
                if (singleContent != null) {
                    singleContent.release();
                    singleContent = null;
                }
                if (buffer != null && !buffer.isEmpty()) {
                    for (int i = 0, size = buffer.size(); i < size; i++) {
                        buffer.get(i).release();
                    }
                    buffer.clear();
                }
                if (parent.inboundHandler() instanceof KoraDecompressingInboundHandler decompressing) {
                    decompressing.dispose();
                }
                handleUpstreamError(new IllegalStateException("Content length exceeded limit: " + maxBodySize + ", actual: " + actualLength));
                if (last) {
                    resetAndRestoreBase();
                    parent.inboundHandler().read(LastHttpContent.EMPTY_LAST_CONTENT);
                }
                return;
            }
            receivedLength += readableBytes;
            if (singleContent == null && (buffer == null || buffer.isEmpty())) {
                singleContent = content;
            } else {
                var list = buffer;
                if (list == null) {
                    list = new ArrayList<>(4);
                    buffer = list;
                }
                if (singleContent != null) {
                    list.add(singleContent);
                    singleContent = null;
                }
                list.add(content);
            }
        } else {
            content.release();
            if (failed) {
                if (last) {
                    resetAndRestoreBase();
                }
                return;
            }
        }

        if (last) {
            ByteBuf fullBody;
            if (singleContent != null) {
                fullBody = singleContent.content();
                singleContent = null;
            } else if (buffer == null || buffer.isEmpty()) {
                fullBody = Unpooled.EMPTY_BUFFER;
            } else {
                var list = buffer;
                int size = list.size();
                var composite = parent.requiredCtx().alloc().compositeBuffer(Math.max(4, size));
                try {
                    for (int i = 0; i < size; i++) {
                        composite.addComponent(true, list.get(i).content());
                    }
                    fullBody = composite;
                } catch (Throwable t) {
                    composite.release();
                    throw t;
                } finally {
                    list.clear();
                }
            }
            receivedLength = 0;
            failed = false;
            var req = Objects.requireNonNull(request);
            request = null;
            var outAccess = Objects.requireNonNull(outboundAccess);
            outboundAccess = null;

            var body = new KoraAvailableBody(parent.bufferFactory().adapt(fullBody));
            parent.setInboundHandler(parent.baseInboundHandler());
            parent.accept(req, body, outAccess);
        }
    }

    private void resetAndRestoreBase() {
        receivedLength = 0;
        failed = false;
        request = null;
        outboundAccess = null;
        parent.setInboundHandler(parent.baseInboundHandler());
    }

    @Override
    void readComplete() {
        devolveToStreaming();
        parent.inboundHandler().readComplete();
    }

    @Override
    void handleUpstreamError(Throwable cause) {
        devolveToStreaming();
        parent.inboundHandler().handleUpstreamError(cause);
    }

    private void devolveToStreaming() {
        if (request == null) {
            return;
        }
        var req = Objects.requireNonNull(request);
        var outAccess = Objects.requireNonNull(outboundAccess);
        request = null;
        outboundAccess = null;

        var streamingBody = new KoraStreamingNettyBody();
        var streamingInboundHandler = new KoraStreamingInboundHandler(
            parent,
            outAccess,
            streamingBody,
            HttpUtil.is100ContinueExpected(req)
        );
        streamingBody.attachHandler(streamingInboundHandler);

        if (parent.inboundHandler() == this) {
            parent.setInboundHandler(streamingInboundHandler);
        } else {
            ((KoraDecompressingInboundHandler) parent.inboundHandler()).setDelegate(streamingInboundHandler);
        }

        if (singleContent != null) {
            var c = singleContent;
            singleContent = null;
            streamingInboundHandler.read(c);
        }
        if (buffer != null && !buffer.isEmpty()) {
            for (var content : buffer) {
                streamingInboundHandler.read(content);
            }
            buffer.clear();
        }
        receivedLength = 0;
        failed = false;

        parent.accept(req, streamingBody, outAccess);
    }

    @Override
    void discard() {
        if (singleContent != null) {
            singleContent.release();
            singleContent = null;
        }
        if (buffer != null && !buffer.isEmpty()) {
            for (var content : buffer) {
                content.release();
            }
            buffer.clear();
        }
        receivedLength = 0;
        failed = false;
    }
}

package io.koraframework.http.server.netty.handler;

import io.koraframework.http.server.netty.body.*;
import io.koraframework.http.server.netty.buffer.*;
import io.netty.handler.codec.http.*;

import java.io.*;

public final class KoraStreamingInboundHandler extends InboundHandler {
    private final PipeliningServerHandler parent;
    private final KoraOutboundAccess outboundAccess;
    private final KoraStreamingNettyBody targetBody;
    private long receivedLength;
    private boolean sendContinue;

    public KoraStreamingInboundHandler(
        PipeliningServerHandler parent,
        KoraOutboundAccess outboundAccess,
        KoraStreamingNettyBody targetBody,
        boolean sendContinue) {
        this.parent = parent;
        this.outboundAccess = outboundAccess;
        this.targetBody = targetBody;
        this.sendContinue = sendContinue;
    }

    @Override
    boolean needMore() {
        return targetBody.isWritable();
    }

    @Override
    void read(Object message) {
        var content = (HttpContent) message;
        boolean last = message.getClass() == DefaultLastHttpContent.class || message instanceof LastHttpContent;
        if (last && content.decoderResult().isFailure()) {
            parent.handleContentDecoderFailure(content);
            return;
        }

        int readableBytes = content.content().readableBytes();
        if (readableBytes > 0) {
            long maxBodySize = parent.bodySizeLimits.maxBodySize();
            if (maxBodySize != Long.MAX_VALUE && readableBytes > maxBodySize - receivedLength) {
                long actualLength = receivedLength > Long.MAX_VALUE - readableBytes ? Long.MAX_VALUE : receivedLength + readableBytes;
                content.release();
                handleUpstreamError(new IllegalStateException("Content length exceeded limit: " + maxBodySize + ", actual: " + actualLength));
                return;
            }
            receivedLength += readableBytes;
            targetBody.add(new KoraReadBuffer(content.content()));
        } else {
            content.release();
        }

        if (last) {
            targetBody.complete();
            if (parent.inboundHandler() == this || (parent.inboundHandler() instanceof KoraDecompressingInboundHandler)) {
                parent.setInboundHandler(parent.baseInboundHandler());
            }
        }
    }

    public void onConsumerStart() {
        if (!sendContinue) {
            return;
        }
        var eventLoop = parent.requiredCtx().channel().eventLoop();
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(new OnConsumerStartTask());
            return;
        }
        if (sendContinue) {
            sendContinue = false;
            outboundAccess.writeContinue();
        }
    }

    public void onConsumerDemandChanged() {
        if (targetBody.isCompleted()) {
            return;
        }
        var eventLoop = parent.requiredCtx().channel().eventLoop();
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(new OnConsumerDemandChangedTask());
            return;
        }
        parent.refreshNeedMore();
    }

    private final class OnConsumerStartTask implements Runnable {
        @Override
        public void run() {
            onConsumerStart();
        }
    }

    private final class OnConsumerDemandChangedTask implements Runnable {
        @Override
        public void run() {
            onConsumerDemandChanged();
        }
    }

    public void onConsumerCancel() {
        var eventLoop = parent.requiredCtx().channel().eventLoop();
        if (!eventLoop.inEventLoop()) {
            eventLoop.execute(new OnConsumerCancelTask());
            return;
        }
        sendContinue = false;
        if (parent.inboundHandler() == this) {
            parent.setInboundHandler(parent.droppingInboundHandler);
            parent.refreshNeedMore();
        } else if (parent.inboundHandler() instanceof KoraDecompressingInboundHandler dec) {
            dec.dispose();
            parent.setInboundHandler(parent.droppingInboundHandler);
            parent.refreshNeedMore();
        }
        targetBody.close();
    }

    private final class OnConsumerCancelTask implements Runnable {
        @Override
        public void run() {
            onConsumerCancel();
        }
    }

    @Override
    void handleUpstreamError(Throwable cause) {
        if (parent.inboundHandler() instanceof KoraDecompressingInboundHandler dih) {
            dih.dispose();
        }
        parent.setInboundHandler(parent.droppingInboundHandler);
        targetBody.error(cause);
    }

    @Override
    void discard() {
        handleUpstreamError(new EOFException("Connection closed before full body was received"));
    }
}

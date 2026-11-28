package io.koraframework.http.server.netty.handler;

import io.netty.buffer.*;
import io.netty.channel.embedded.*;
import io.netty.handler.codec.compression.*;
import io.netty.handler.codec.http.*;

public final class KoraDecompressingInboundHandler extends InboundHandler {
    private final PipeliningServerHandler parent;
    private final EmbeddedChannel decompressionChannel;
    private InboundHandler delegate;

    public KoraDecompressingInboundHandler(PipeliningServerHandler parent, EmbeddedChannel decompressionChannel, InboundHandler delegate) {
        this.parent = parent;
        this.decompressionChannel = decompressionChannel;
        this.delegate = delegate;
    }

    public void setDelegate(InboundHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    boolean needMore() {
        return delegate.needMore();
    }

    @Override
    void read(Object message) {
        var content = (HttpContent) message;
        var last = message instanceof LastHttpContent;
        if (last && content.decoderResult().isFailure()) {
            parent.handleContentDecoderFailure(content);
            return;
        }

        var compressed = content.content();
        if (!compressed.isReadable() && !last) {
            delegate.read(message);
            return;
        }

        try {
            decompressionChannel.writeInbound(compressed.retain());
            drainDecompressed();

            if (last) {
                if (decompressionChannel.isOpen()) {
                    decompressionChannel.finish();
                }
                drainDecompressed();
                var trailingHeaders = ((LastHttpContent) message).trailingHeaders();
                delegate.read(trailingHeaders.isEmpty()
                    ? LastHttpContent.EMPTY_LAST_CONTENT
                    : new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, trailingHeaders));
            }
        } catch (DecompressionException e) {
            dispose();
            delegate.handleUpstreamError(e);
            if (last) {
                parent.inboundHandler().read(LastHttpContent.EMPTY_LAST_CONTENT);
            }
        } finally {
            content.release();
        }
    }

    private void drainDecompressed() {
        while (true) {
            ByteBuf decompressed = decompressionChannel.readInbound();
            if (decompressed == null) {
                break;
            }
            if (!decompressed.isReadable()) {
                decompressed.release();
                continue;
            }
            delegate.read(new DefaultHttpContent(decompressed));
        }
    }

    void dispose() {
        if (decompressionChannel.pipeline().context("decompressor") != null) {
            decompressionChannel.pipeline().remove("decompressor");
        }
        if (!decompressionChannel.isOpen()) {
            return;
        }
        try {
            decompressionChannel.finishAndReleaseAll();
        } catch (DecompressionException ignored) {
        }
    }

    @Override
    void handleUpstreamError(Throwable cause) {
        delegate.handleUpstreamError(cause);
    }

    @Override
    void readComplete() {
        delegate.readComplete();
    }

    @Override
    void discard() {
        dispose();
        delegate.discard();
    }
}

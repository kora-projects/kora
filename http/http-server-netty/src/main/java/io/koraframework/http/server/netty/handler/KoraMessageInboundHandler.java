package io.koraframework.http.server.netty.handler;

import io.koraframework.http.server.netty.body.*;
import io.koraframework.http.server.netty.request.*;
import io.netty.channel.*;
import io.netty.channel.embedded.*;
import io.netty.handler.codec.compression.*;
import io.netty.handler.codec.http.*;
import io.netty.util.*;
import org.jspecify.annotations.*;

public final class KoraMessageInboundHandler extends InboundHandler {
    private final PipeliningServerHandler parent;

    public KoraMessageInboundHandler(PipeliningServerHandler parent) {
        this.parent = parent;
    }

    @Override
    void read(Object message) {
        if (!(message instanceof HttpRequest request)) {
            if (message instanceof LastHttpContent last && last.decoderResult().isFailure()) {
                parent.closeAfterContentDecoderFailure();
            }
            ReferenceCountUtil.release(message);
            return;
        }

        var ctx = parent.requiredCtx();
        var outboundAccess = new KoraOutboundAccess(request, ctx, parent);
        parent.outboundQueue.add(outboundAccess);

        boolean full = request.getClass() != DefaultHttpRequest.class && request instanceof FullHttpRequest;
        boolean hasBody = !full && parent.hasBody(request);

        EmbeddedChannel decompressionChannel = null;
        if (parent.requestDecompressionEnabled && (full || hasBody)) {
            var contentEncoding = getContentEncoding(request);
            if (contentEncoding != null) {
                if (HttpHeaderValues.GZIP.contentEqualsIgnoreCase(contentEncoding) || HttpHeaderValues.X_GZIP.contentEqualsIgnoreCase(contentEncoding)) {
                    decompressionChannel = newDecompressionChannel(ctx, ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP, maxZlibAllocation()));
                } else if (HttpHeaderValues.DEFLATE.contentEqualsIgnoreCase(contentEncoding) || HttpHeaderValues.X_DEFLATE.contentEqualsIgnoreCase(contentEncoding)) {
                    decompressionChannel = newDecompressionChannel(ctx, ZlibCodecFactory.newZlibDecoder(ZlibWrapper.ZLIB_OR_NONE, maxZlibAllocation()));
                }
                if (decompressionChannel != null) {
                    var headers = request.headers();
                    headers.remove(HttpHeaderNames.CONTENT_LENGTH);
                    headers.remove(HttpHeaderNames.CONTENT_ENCODING);
                    headers.add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                }
            }
        }

        if (full && decompressionChannel == null) {
            var fullRequest = (FullHttpRequest) request;
            var body = new KoraAvailableBody(parent.bufferFactory().adapt(fullRequest.content().retain()));
            parent.accept(request, body, outboundAccess);
        } else if (!full && !hasBody) {
            parent.setInboundHandler(request.decoderResult().isFailure() ? parent.droppingInboundHandler : parent.noBodyInboundHandler);
            parent.accept(request, null, outboundAccess);
        } else {
            var bufferingHandler = parent.optimisticBufferingInboundHandler();
            bufferingHandler.init(request, outboundAccess);
            if (decompressionChannel == null) {
                parent.setInboundHandler(bufferingHandler);
            } else {
                parent.setInboundHandler(new KoraDecompressingInboundHandler(parent, decompressionChannel, bufferingHandler));
            }
            if (full) {
                var fullRequest = (FullHttpRequest) request;
                parent.inboundHandler().read(new DefaultLastHttpContent(fullRequest.content().retain(), fullRequest.trailingHeaders()));
            }
        }
    }


    private static EmbeddedChannel newDecompressionChannel(ChannelHandlerContext ctx, ChannelHandler decompressor) {
        var ch = ctx.channel();
        return new EmbeddedChannel(
            ch.id(),
            ch.metadata().hasDisconnect(),
            ch.config(),
            decompressor
        );
    }

    private int maxZlibAllocation() {
        long maxBufferSize = parent.bodySizeLimits.maxBufferSize();
        if (maxBufferSize == Long.MAX_VALUE || maxBufferSize > Integer.MAX_VALUE) {
            return 0;
        }
        return (int) Math.max(1, maxBufferSize);
    }

    @Nullable
    private static String getContentEncoding(HttpRequest request) {
        var headers = request.headers();
        if (headers.isEmpty()) {
            return null;
        }
        var contentEncoding = headers.get(HttpHeaderNames.CONTENT_ENCODING);
        if (contentEncoding != null) {
            return contentEncoding.trim();
        }
        if (request instanceof KoraHttpRequest) {
            return null;
        }
        var transferEncoding = headers.get(HttpHeaderNames.TRANSFER_ENCODING);
        if (transferEncoding != null) {
            int idx = transferEncoding.indexOf(',');
            if (idx != -1) {
                return transferEncoding.substring(0, idx).trim();
            }
            return transferEncoding.trim();
        }
        return null;
    }

    @Override
    void handleUpstreamError(Throwable cause) {
        parent.requestHandler.handleUnboundError(cause);
    }
}

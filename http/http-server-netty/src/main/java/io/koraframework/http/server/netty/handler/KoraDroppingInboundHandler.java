package io.koraframework.http.server.netty.handler;

import io.netty.handler.codec.http.*;

public final class KoraDroppingInboundHandler extends InboundHandler {
    private final PipeliningServerHandler parent;

    public KoraDroppingInboundHandler(PipeliningServerHandler parent) {
        this.parent = parent;
    }

    @Override
    void read(Object message) {
        if (message instanceof LastHttpContent lhc) {
            lhc.release();
            parent.setInboundHandler(parent.baseInboundHandler());
            if (lhc.decoderResult().isFailure() && !(lhc instanceof HttpRequest)) {
                parent.closeAfterContentDecoderFailure();
            }
        } else {
            ((HttpContent) message).release();
        }
    }

    @Override
    void handleUpstreamError(Throwable cause) {
        parent.requestHandler.handleUnboundError(cause);
    }
}

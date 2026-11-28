package io.koraframework.http.server.netty.handler;

import io.netty.handler.codec.http.*;

import static io.netty.handler.codec.http.HttpUtil.*;

final class FullOutboundHandler extends OutboundHandler {
    private final FullHttpResponse message;
    private boolean written = false;

    FullOutboundHandler(KoraOutboundAccess outboundAccess, FullHttpResponse message, PipeliningServerHandler parent) {
        super(outboundAccess, parent);
        this.message = message;
    }

    @Override
    void markCloseAfterWrite() {
        if (!written) {
            setKeepAlive(message, false);
        }
    }

    @Override
    void writeSome() {
        written = true;
        try {
            writeCompressing(message, true, true);
        } catch (RuntimeException | Error e) {
            parent.requestHandler.responseFailed(outboundAccess.attachment(), e);
            parent.setOutboundHandler(null);
            super.discardOutbound();
            markResponseWritten();
            parent.requiredCtx().flush();
            parent.requiredCtx().close();
            return;
        }
        parent.setOutboundHandler(null);
        markResponseWritten();
    }

    @Override
    void discardOutbound() {
        super.discardOutbound();
        parent.setOutboundHandler(null);
        markResponseWritten();
        if (!written) {
            message.release();
        }
    }

    @Override
    void discardRefused() {
        super.discardRefused();
        message.release();
    }
}

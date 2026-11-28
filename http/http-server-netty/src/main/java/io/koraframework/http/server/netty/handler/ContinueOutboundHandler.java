package io.koraframework.http.server.netty.handler;

import io.netty.buffer.*;
import io.netty.handler.codec.http.*;
import org.jspecify.annotations.*;

final class ContinueOutboundHandler extends OutboundHandler {
    static final FullHttpResponse CONTINUE_11 = new DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1,
        HttpResponseStatus.CONTINUE,
        Unpooled.EMPTY_BUFFER
    );

    boolean written = false;
    @Nullable OutboundHandler next;

    ContinueOutboundHandler(KoraOutboundAccess outboundAccess, PipeliningServerHandler parent) {
        super(outboundAccess, parent);
    }

    @Override
    void writeSome() {
        if (!written) {
            parent.write0(CONTINUE_11, true, false, false, null);
            written = true;
        }
        if (next != null) {
            parent.setOutboundHandler(next);
        }
    }

    @Override
    void markCloseAfterWrite() {
        if (next != null) {
            next.markCloseAfterWrite();
        }
    }

    @Override
    void discardOutbound() {
        super.discardOutbound();
        if (next != null) {
            next.discardOutbound();
            next = null;
        }
    }
}

package io.koraframework.http.server.netty.handler;

import static io.netty.util.ReferenceCountUtil.*;

public final class KoraClosedInboundHandler extends InboundHandler {
    @Override
    void read(Object message) {
        release(message);
    }

    @Override
    void handleUpstreamError(Throwable cause) {
    }
}

package io.koraframework.http.server.netty.handler;

final class ActionOutboundHandler extends OutboundHandler {
    private final Runnable action;
    private final Runnable discard;
    private boolean done = false;

    ActionOutboundHandler(KoraOutboundAccess outboundAccess, Runnable action, Runnable discard, PipeliningServerHandler parent) {
        super(outboundAccess, parent);
        this.action = action;
        this.discard = discard;
    }

    @Override
    void writeSome() {
        if (!done) {
            done = true;
            action.run();
        }
    }

    @Override
    void markCloseAfterWrite() {
    }

    @Override
    void discardOutbound() {
        super.discardOutbound();
        if (!done) {
            done = true;
            discard.run();
            markResponseWritten();
        }
    }

    @Override
    void discardRefused() {
        super.discardRefused();
        discard.run();
    }
}

package io.koraframework.http.server.netty.handler;

public abstract class InboundHandler {

    boolean needMore() {
        return true;
    }

    abstract void read(Object message);

    abstract void handleUpstreamError(Throwable cause);

    void readComplete() {
    }

    void discard() {
    }
}

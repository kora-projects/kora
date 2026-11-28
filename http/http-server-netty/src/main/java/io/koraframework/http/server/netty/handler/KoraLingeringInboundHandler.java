package io.koraframework.http.server.netty.handler;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.*;
import io.netty.util.concurrent.ScheduledFuture;
import org.jspecify.annotations.*;

import java.util.concurrent.*;

public final class KoraLingeringInboundHandler extends InboundHandler implements Runnable {
    private static final long LINGER_IDLE_MILLIS = 5_000;
    private static final long LINGER_MAX_MILLIS = 20_000;
    private static final long LINGER_MAX_BYTES = 2L * 1024 * 1024;

    private final ChannelHandlerContext context;
    private final long start = System.nanoTime();
    private long lastRead = start;
    private long dropped;
    @Nullable
    private ScheduledFuture<?> timeout;
    private boolean closed;

    public KoraLingeringInboundHandler(ChannelHandlerContext context) {
        this.context = context;
    }

    public void scheduleTimeout(long delayMillis) {
        if (!closed) {
            timeout = context.executor().schedule(this, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void run() {
        checkTimeout();
    }

    public void cancelTimeout() {
        closed = true;
        if (timeout != null) {
            timeout.cancel(false);
            timeout = null;
        }
    }

    private void checkTimeout() {
        if (closed) {
            return;
        }
        long now = System.nanoTime();
        long idleLeft = LINGER_IDLE_MILLIS - TimeUnit.NANOSECONDS.toMillis(now - lastRead);
        long totalLeft = LINGER_MAX_MILLIS - TimeUnit.NANOSECONDS.toMillis(now - start);
        if (idleLeft <= 0 || totalLeft <= 0) {
            cancelTimeout();
            context.close();
        } else {
            scheduleTimeout(Math.min(idleLeft, totalLeft));
        }
    }

    @Override
    void read(Object message) {
        lastRead = System.nanoTime();
        if (message instanceof HttpContent content) {
            dropped += content.content().readableBytes();
        }
        boolean last = message instanceof LastHttpContent;
        ReferenceCountUtil.release(message);
        if (last || dropped > LINGER_MAX_BYTES) {
            cancelTimeout();
            context.close();
        }
    }

    @Override
    void handleUpstreamError(Throwable cause) {
        cancelTimeout();
        context.close();
    }
}

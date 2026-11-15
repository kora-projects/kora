package io.koraframework.http.server.netty.handler;

import io.koraframework.http.server.netty.buffer.*;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.jspecify.annotations.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.Flow.*;

final class KoraStreamingOutboundHandler extends OutboundHandler implements Subscriber<KoraReadBuffer> {
    private static final Logger LOG = LoggerFactory.getLogger(KoraStreamingOutboundHandler.class);

    @Nullable
    private HttpResponse initialMessage;
    @Nullable
    private Subscription subscription;

    private boolean headWritten = false;
    private boolean completed = false;
    private boolean refused = false;
    private boolean completedBeforeOpen = false;
    @Nullable
    private Throwable errorBeforeOpen;

    KoraStreamingOutboundHandler(KoraOutboundAccess outboundAccess, HttpResponse initialMessage, PipeliningServerHandler parent) {
        super(outboundAccess, parent);
        if (initialMessage instanceof FullHttpResponse) {
            throw new IllegalArgumentException("Cannot have a full response as the initial message of a streaming response");
        }
        this.initialMessage = Objects.requireNonNull(initialMessage, "initialMessage");
    }

    @Override
    void markCloseAfterWrite() {
        var message = initialMessage;
        if (message != null) {
            HttpUtil.setKeepAlive(message, false);
        }
    }

    @Override
    void writeSome() {
        if (!headWritten && initialMessage != null) {
            headWritten = true;
            writeIntermediate(initialMessage, false, false);
            initialMessage = null;
            if (errorBeforeOpen != null) {
                var err = errorBeforeOpen;
                errorBeforeOpen = null;
                fail(err);
                return;
            }
            if (completedBeforeOpen) {
                finishResponse();
                return;
            }
        }

        var sub = subscription;
        if (sub != null && !completed && !refused && headWritten && parent.requiredCtx().channel().isWritable()) {
            sub.request(1);
        }
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        var eventLoop = parent.requiredCtx().channel().eventLoop();
        if (!eventLoop.inEventLoop()) {
            try {
                eventLoop.execute(new OnSubscribeTask(subscription));
            } catch (Throwable t) {
                subscription.cancel();
            }
            return;
        }
        this.subscription = subscription;
        if (refused || completed) {
            subscription.cancel();
            return;
        }
        if (parent.outboundHandler == this) {
            writeSome();
        }
    }

    @Override
    @SuppressWarnings("java:S1181")
    public void onNext(KoraReadBuffer item) {
        var eventLoop = parent.requiredCtx().channel().eventLoop();
        if (!eventLoop.inEventLoop()) {
            try {
                eventLoop.execute(new OnNextTask(item));
            } catch (Throwable t) {
                item.close();
            }
            return;
        }
        if (refused || completed || !headWritten) {
            item.close();
            return;
        }

        ByteBuf data;
        try {
            data = item.toByteBuf();
        } catch (Throwable t) {
            fail(t);
            return;
        }

        try {
            writeCompressing(new DefaultHttpContent(data), true, false);
            var sub = subscription;
            if (sub != null && !completed && !refused && parent.requiredCtx().channel().isWritable()) {
                sub.request(1);
            }
        } catch (RuntimeException | Error e) {
            fail(e);
        }
    }

    @Override
    @SuppressWarnings("java:S1181")
    public void onComplete() {
        var eventLoop = parent.requiredCtx().channel().eventLoop();
        if (!eventLoop.inEventLoop()) {
            try {
                eventLoop.execute(new OnCompleteTask());
            } catch (Throwable ignored) {
            }
            return;
        }
        if (completed || refused) {
            return;
        }
        completed = true;
        if (!headWritten) {
            completedBeforeOpen = true;
            return;
        }
        finishResponse();
    }

    private final class OnSubscribeTask implements Runnable {
        private final Subscription subscription;

        private OnSubscribeTask(Subscription subscription) {
            this.subscription = subscription;
        }

        @Override
        public void run() {
            onSubscribe(subscription);
        }
    }

    private final class OnNextTask implements Runnable {
        private final KoraReadBuffer item;

        private OnNextTask(KoraReadBuffer item) {
            this.item = item;
        }

        @Override
        public void run() {
            onNext(item);
        }
    }

    private final class OnCompleteTask implements Runnable {
        @Override
        public void run() {
            onComplete();
        }
    }

    private void finishResponse() {
        parent.setOutboundHandler(null);
        try {
            writeCompressing(LastHttpContent.EMPTY_LAST_CONTENT, true, true);
        } catch (RuntimeException | Error e) {
            fail(e);
            return;
        }

        if (!refused) {
            markResponseWritten();
        }
        parent.writeSome();
    }

    @Override
    public void onError(Throwable throwable) {
        var eventLoop = parent.requiredCtx().channel().eventLoop();
        if (!eventLoop.inEventLoop()) {
            try {
                eventLoop.execute(new OnErrorTask(throwable));
            } catch (Throwable ignored) {
            }
            return;
        }
        if (completed || refused) {
            return;
        }
        if (!headWritten) {
            completed = true;
            errorBeforeOpen = throwable;
            return;
        }
        fail(throwable);
    }

    private final class OnErrorTask implements Runnable {
        private final Throwable throwable;

        private OnErrorTask(Throwable throwable) {
            this.throwable = throwable;
        }

        @Override
        public void run() {
            onError(throwable);
        }
    }

    private void fail(Throwable t) {
        if (refused) {
            return;
        }
        completed = true;

        parent.requestHandler.responseFailed(outboundAccess.attachment(), t);
        if (LOG.isWarnEnabled()) {
            if (!headWritten) {
                LOG.warn("The streamed response body failed before the response was written. The error cannot be forwarded to the client.", t);
            } else {
                LOG.warn("The streamed response body failed after some of it was written. The error cannot be forwarded to the client, and the response ends abruptly.", t);
            }
        }

        parent.setOutboundHandler(null);
        discardOutbound();
        parent.requiredCtx().writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    void discardOutbound() {
        super.discardOutbound();
        completed = true;
        var sub = subscription;
        if (sub != null) {
            subscription = null;
            sub.cancel();
        }
        parent.setOutboundHandler(null);
    }

    @Override
    void discardRefused() {
        super.discardRefused();
        refused = true;
        completed = true;
        var eventLoop = parent.requiredCtx().channel().eventLoop();
        if (eventLoop.inEventLoop()) {
            cancelSubscription();
        } else {
            try {
                eventLoop.execute(new CancelSubscriptionTask());
            } catch (Throwable ignored) {
            }
        }
    }

    private void cancelSubscription() {
        var sub = subscription;
        if (sub != null) {
            subscription = null;
            sub.cancel();
        }
    }

    private final class CancelSubscriptionTask implements Runnable {
        @Override
        public void run() {
            cancelSubscription();
        }
    }
}

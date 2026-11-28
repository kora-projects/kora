package io.koraframework.http.server.netty;

import io.koraframework.common.context.*;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.jspecify.annotations.*;

public record NettyContext(
    HttpRequest request,
    ChannelHandlerContext channelContext
) {

    public static final KoraContext.Key<NettyContext> NETTY_KEY = KoraContext.Key.create();

    @Nullable
    public static NettyContext get() {
        KoraContext current = KoraContext.current();
        return current != null ? current.get(NETTY_KEY) : null;
    }

    @Nullable
    public static HttpRequest getRequest() {
        NettyContext nettyCtx = get();
        return nettyCtx != null ? nettyCtx.request : null;
    }

    @Nullable
    public static ChannelHandlerContext getChannelContext() {
        NettyContext nettyCtx = get();
        return nettyCtx != null ? nettyCtx.channelContext : null;
    }
}

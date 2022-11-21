package ru.tinkoff.kora.vertx.common;

import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.netty.common.NettyCommonModule;

import java.util.concurrent.ThreadFactory;

public interface VertxCommonModule extends NettyCommonModule {
    @Tag(NettyCommonModule.class)
    default ThreadFactory vertxNettyThreadFactory() {
        return VertxUtil.vertxThreadFactory();
    }
}

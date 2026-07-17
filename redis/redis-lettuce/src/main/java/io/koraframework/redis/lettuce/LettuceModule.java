package io.koraframework.redis.lettuce;

import io.koraframework.common.annotation.FactoryModule;
import io.koraframework.netty.common.NettyModule;

public interface LettuceModule extends NettyModule {

    @FactoryModule
    default LettuceFactoryModule lettuce() {
        return new LettuceFactoryModule("lettuce");
    }
}

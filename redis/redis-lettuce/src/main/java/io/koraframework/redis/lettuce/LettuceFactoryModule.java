package io.koraframework.redis.lettuce;

import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.netty.common.NettyModule;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.metrics.CommandLatencyRecorder;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.EventLoopGroup;
import org.jspecify.annotations.Nullable;

public class LettuceFactoryModule {

    private final String configPath;

    public LettuceFactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public LettuceConfig lettuceConfig(Config config, ConfigValueMapper<LettuceConfig> mapper) {
        return mapper.mapOrThrow(config.get(this.configPath));
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public LettuceFactory lettuceFactory(@Nullable MeterRegistry meterRegistry,
                                         @Nullable CommandLatencyRecorder commandLatencyRecorder,
                                         @Tag(NettyModule.EventLoopWorker.class) @Nullable EventLoopGroup eventLoopGroup,
                                         @Tag(Tag.Factory.class) @Nullable Configurer<DefaultClientResources.Builder> resourcesConfigurer,
                                         @Tag(Tag.Factory.class) @Nullable Configurer<ClientOptions.Builder> standaloneOptionsConfigurer,
                                         @Tag(Tag.Factory.class) @Nullable Configurer<ClusterClientOptions.Builder> clusterOptionsConfigurer) {
        return new LettuceFactory(meterRegistry, commandLatencyRecorder, eventLoopGroup, resourcesConfigurer, standaloneOptionsConfigurer, clusterOptionsConfigurer);
    }

    @DefaultComponent
    @Tag(Tag.Factory.class)
    public AbstractRedisClient lettuceRedisClient(@Tag(Tag.Factory.class) LettuceFactory factory,
                                                  @Tag(Tag.Factory.class) LettuceConfig config) {
        return factory.build(config);
    }
}

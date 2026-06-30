package io.koraframework.redis.lettuce;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.common.Configurer;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.netty.common.NettyModule;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.metrics.CommandLatencyRecorder;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.EventLoopGroup;
import org.jspecify.annotations.Nullable;

public interface LettuceModule extends NettyModule {

    default LettuceConfig lettuceConfig(Config config, ConfigValueExtractor<LettuceConfig> extractor) {
        return extractor.extractOrThrow(config.get("lettuce"));
    }

    @DefaultComponent
    default LettuceFactory lettuceFactory(@Nullable MeterRegistry meterRegistry,
                                          @Nullable CommandLatencyRecorder commandLatencyRecorder,
                                          @Tag(NettyModule.EventLoopWorker.class) @Nullable EventLoopGroup eventLoopGroup,
                                          @Nullable Configurer<DefaultClientResources.Builder> resourcesConfigurer,
                                          @Nullable Configurer<ClientOptions.Builder> standaloneOptionsConfigurer,
                                          @Nullable Configurer<ClusterClientOptions.Builder> clusterOptionsConfigurer) {
        return new LettuceFactory(meterRegistry, commandLatencyRecorder, eventLoopGroup, resourcesConfigurer, standaloneOptionsConfigurer, clusterOptionsConfigurer);
    }

    @DefaultComponent
    default AbstractRedisClient lettuceRedisClient(LettuceFactory factory,
                                                   LettuceConfig config) {
        return factory.build(config);
    }
}

package io.koraframework.redis.lettuce;

import io.koraframework.redis.lettuce.telemetry.$LettuceTelemetryConfig_ConfigValueMapper;
import io.koraframework.redis.lettuce.telemetry.$LettuceTelemetryConfig_LettuceLoggingConfig_ConfigValueMapper;
import io.koraframework.redis.lettuce.telemetry.$LettuceTelemetryConfig_LettuceMetricsConfig_ConfigValueMapper;
import io.koraframework.redis.lettuce.telemetry.LettuceTelemetryConfig;
import io.koraframework.test.redis.RedisParams;
import io.koraframework.test.redis.RedisTestContainer;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.codec.StringCodec;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RedisTestContainer
class LettuceFactoryTest {

    private static LettuceConfig config(String uri, boolean forceCluster) {
        return new LettuceConfig() {
            @Override
            public String uri() {
                return uri;
            }

            @Override
            public boolean forceClusterClient() {
                return forceCluster;
            }

            @Override
            public Integer database() {
                return null;
            }

            @Override
            public String user() {
                return null;
            }

            @Override
            public String password() {
                return null;
            }

            @Override
            public SslConfig ssl() {
                return new $LettuceConfig_SslConfig_ConfigValueMapper.SslConfig_Defaults();
            }

            @Override
            public LettuceTelemetryConfig telemetry() {
                return new $LettuceTelemetryConfig_ConfigValueMapper.LettuceTelemetryConfig_Impl(
                    new $LettuceTelemetryConfig_LettuceLoggingConfig_ConfigValueMapper.LettuceLoggingConfig_Defaults(),
                    new $LettuceTelemetryConfig_LettuceMetricsConfig_ConfigValueMapper.LettuceMetricsConfig_Defaults());
            }
        };
    }

    private static LettuceFactory factory(@Nullable EventLoopGroup eventLoopGroup) {
        return new LettuceFactoryModule("lettuce").lettuceFactory(null, null, eventLoopGroup, null, null, null);
    }

    private static EventLoopGroup workerGroup() {
        return new MultiThreadIoEventLoopGroup(2, new DefaultThreadFactory("netty-kora-worker", true), NioIoHandler.newFactory());
    }

    private static void assertResourcesShutDown(AbstractRedisClient client, EventLoopGroup workerGroup) {
        assertThatThrownBy(() -> client.getResources().timer().newTimeout(t -> {}, 1, TimeUnit.SECONDS))
            .isInstanceOf(IllegalStateException.class);
        assertThat(workerGroup.isShuttingDown()).isFalse();
    }

    @Test
    void standaloneClientCloseShutsDownOwnResourcesButKeepsWorkerGroup() throws Exception {
        var workerGroup = workerGroup();
        try {
            var client = factory(workerGroup).build(config("redis://localhost:6379", false));
            assertThat(client).isInstanceOf(RedisClient.class);

            client.close();
            client.close();

            assertResourcesShutDown(client, workerGroup);
        } finally {
            workerGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).get();
        }
    }

    @Test
    void clusterClientCloseShutsDownOwnResourcesButKeepsWorkerGroup() throws Exception {
        var workerGroup = workerGroup();
        try {
            var client = factory(workerGroup).build(config("redis://localhost:6379", true));
            assertThat(client).isInstanceOf(RedisClusterClient.class);

            client.close();

            assertResourcesShutDown(client, workerGroup);
        } finally {
            workerGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).get();
        }
    }

    @Test
    void clientWithoutWorkerGroupCloseShutsDownOwnExecutors() {
        var client = factory(null).build(config("redis://localhost:6379", false));

        client.close();

        assertThat(client.getResources().eventExecutorGroup().isShutdown()).isTrue();
    }

    @Test
    void repeatedInitReleaseDoesNotLeakLettuceTimerThreads(RedisParams params) throws Exception {
        var config = config(params.uri().toString(), false);
        Runnable cycle = () -> {
            var workerGroup = workerGroup();
            var client = (RedisClient) factory(workerGroup).build(config);
            try (var connection = client.connect(StringCodec.UTF8)) {
                connection.sync().set("k", "v");
            }
            try {
                client.close();
                workerGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        cycle.run();
        var before = lettuceTimerThreads();
        for (int i = 0; i < 10; i++) {
            cycle.run();
        }
        Thread.sleep(1000);

        assertThat(lettuceTimerThreads()).isLessThanOrEqualTo(before);
    }

    private static long lettuceTimerThreads() {
        return Thread.getAllStackTraces().keySet().stream()
            .filter(Thread::isAlive)
            .filter(t -> t.getName().startsWith("lettuce-timer"))
            .count();
    }
}

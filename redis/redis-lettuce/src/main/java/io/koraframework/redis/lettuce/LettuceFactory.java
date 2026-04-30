package io.koraframework.redis.lettuce;

import io.koraframework.common.util.Configurer;
import io.koraframework.redis.lettuce.telemetry.DefaultLettuceTelemetry;
import io.lettuce.core.*;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.RedisClusterURIUtil;
import io.lettuce.core.metrics.CommandLatencyRecorder;
import io.lettuce.core.protocol.ProtocolVersion;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.EventLoopGroup;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Duration;
import java.util.List;

public class LettuceFactory {

    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final EventLoopGroup eventLoopGroup;
    @Nullable
    private final CommandLatencyRecorder commandLatencyRecorder;

    @Nullable
    private final Configurer<DefaultClientResources.Builder> resourcesConfigurer;
    @Nullable
    private final Configurer<ClientOptions.Builder> standaloneOptionsConfigurer;
    @Nullable
    private final Configurer<ClusterClientOptions.Builder> clusterOptionsConfigurer;

    public LettuceFactory(@Nullable MeterRegistry meterRegistry,
                          @Nullable CommandLatencyRecorder commandLatencyRecorder,
                          @Nullable EventLoopGroup eventLoopGroup,
                          @Nullable Configurer<DefaultClientResources.Builder> resourcesConfigurer,
                          @Nullable Configurer<ClientOptions.Builder> standaloneOptionsConfigurer,
                          @Nullable Configurer<ClusterClientOptions.Builder> clusterOptionsConfigurer) {
        this.meterRegistry = meterRegistry;
        this.commandLatencyRecorder = commandLatencyRecorder;
        this.eventLoopGroup = eventLoopGroup;
        this.resourcesConfigurer = resourcesConfigurer;
        this.standaloneOptionsConfigurer = standaloneOptionsConfigurer;
        this.clusterOptionsConfigurer = clusterOptionsConfigurer;
    }

    public AbstractRedisClient build(LettuceConfig config) {
        final List<RedisURI> mappedRedisUris = buildRedisURI(config);

        return (mappedRedisUris.size() == 1 && !config.forceClusterClient())
            ? buildStandalone(config)
            : buildCluster(config);
    }

    public ClientOptions.Builder buildStandaloneConfig(LettuceConfig config) {
        final ProtocolVersion protocolVersion = switch (config.protocol()) {
            case RESP2 -> ProtocolVersion.RESP2;
            case RESP3 -> ProtocolVersion.RESP3;
        };

        return ClientOptions.builder()
            .autoReconnect(true)
            .publishOnScheduler(true)
            .suspendReconnectOnProtocolFailure(false)
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
            .protocolVersion(protocolVersion)
            .sslOptions(SslOptions.builder()
                .cipherSuites(config.ssl().ciphers().toArray(new String[0]))
                .handshakeTimeout(config.ssl().handshakeTimeout())
                .build())
            .timeoutOptions(TimeoutOptions.builder()
                .connectionTimeout()
                .fixedTimeout(config.commandTimeout())
                .timeoutCommands(true)
                .build())
            .socketOptions(SocketOptions.builder()
                .keepAlive(true)
                .connectTimeout(config.socketTimeout())
                .build());
    }

    public RedisClient buildStandalone(LettuceConfig config) {
        final List<RedisURI> redisURIs = buildRedisURI(config);
        final CommandLatencyRecorder recorder;
        if (config.telemetry().metrics().enabled()) {
            if (this.commandLatencyRecorder != null) {
                recorder = this.commandLatencyRecorder;
            } else if (this.meterRegistry != null) {
                recorder = new DefaultLettuceTelemetry("standalone", meterRegistry, config.telemetry());
            } else {
                recorder = CommandLatencyRecorder.disabled();
            }
        } else {
            recorder = CommandLatencyRecorder.disabled();
        }

        var clientResourcesBuilder = DefaultClientResources.builder()
            .commandLatencyRecorder(recorder);

        if (eventLoopGroup != null) {
            clientResourcesBuilder = clientResourcesBuilder.eventExecutorGroup(eventLoopGroup);
        }
        if (resourcesConfigurer != null) {
            clientResourcesBuilder = resourcesConfigurer.configure(clientResourcesBuilder);
        }

        final RedisClient client = RedisClient.create(clientResourcesBuilder.build(), redisURIs.getFirst());
        var clientBuilder = buildStandaloneConfig(config);
        if (standaloneOptionsConfigurer != null) {
            clientBuilder = standaloneOptionsConfigurer.configure(clientBuilder);
        }

        client.setOptions(clientBuilder.build());
        return client;
    }

    public ClusterClientOptions.Builder buildClusterConfig(LettuceConfig config) {
        final Duration commandTimeout = config.commandTimeout();
        final Duration socketTimeout = config.socketTimeout();
        final ProtocolVersion protocolVersion = switch (config.protocol()) {
            case RESP2 -> ProtocolVersion.RESP2;
            case RESP3 -> ProtocolVersion.RESP3;
        };

        return ClusterClientOptions.builder()
            .autoReconnect(true)
            .publishOnScheduler(true)
            .suspendReconnectOnProtocolFailure(false)
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.DEFAULT)
            .protocolVersion(protocolVersion)
            .sslOptions(SslOptions.builder()
                .cipherSuites(config.ssl().ciphers().toArray(new String[0]))
                .handshakeTimeout(config.ssl().handshakeTimeout())
                .build())
            .timeoutOptions(TimeoutOptions.builder()
                .connectionTimeout()
                .fixedTimeout(commandTimeout)
                .timeoutCommands(true)
                .build())
            .socketOptions(SocketOptions.builder()
                .keepAlive(true)
                .connectTimeout(socketTimeout)
                .build());
    }

    public RedisClusterClient buildCluster(LettuceConfig config) {
        final List<RedisURI> redisURIs = buildRedisURI(config);
        final CommandLatencyRecorder recorder;
        if (config.telemetry().metrics().enabled()) {
            if (this.commandLatencyRecorder != null) {
                recorder = this.commandLatencyRecorder;
            } else if (this.meterRegistry != null) {
                recorder = new DefaultLettuceTelemetry("cluster", meterRegistry, config.telemetry());
            } else {
                recorder = CommandLatencyRecorder.disabled();
            }
        } else {
            recorder = CommandLatencyRecorder.disabled();
        }

        var clientResourcesBuilder = DefaultClientResources.builder()
            .commandLatencyRecorder(recorder);

        if (eventLoopGroup != null) {
            clientResourcesBuilder = clientResourcesBuilder.eventExecutorGroup(eventLoopGroup);
        }
        if (resourcesConfigurer != null) {
            clientResourcesBuilder = resourcesConfigurer.configure(clientResourcesBuilder);
        }

        final RedisClusterClient client = RedisClusterClient.create(clientResourcesBuilder.build(), redisURIs);
        var clusterBuilder = buildClusterConfig(config);
        if (clusterOptionsConfigurer != null) {
            clusterBuilder = clusterOptionsConfigurer.configure(clusterBuilder);
        }

        client.setOptions(clusterBuilder.build());
        return client;
    }

    public List<RedisURI> buildRedisURI(LettuceConfig config) {
        final String uri = config.uri();
        final Integer database = config.database();
        final String user = config.user();
        final String password = config.password();

        final List<RedisURI> redisURIS = RedisClusterURIUtil.toRedisURIs(URI.create(uri));
        return redisURIS.stream()
            .map(redisURI -> {
                RedisURI.Builder builder = RedisURI.builder(redisURI);
                if (database != null) {
                    builder = builder.withDatabase(database);
                }
                if (user != null && password != null) {
                    builder = builder.withAuthentication(user, password);
                } else if (password != null) {
                    builder = builder.withPassword(((CharSequence) password));
                }

                return builder
                    .withTimeout(config.commandTimeout())
                    .build();
            })
            .toList();
    }
}

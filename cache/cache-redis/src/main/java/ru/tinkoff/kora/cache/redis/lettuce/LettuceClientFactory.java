package ru.tinkoff.kora.cache.redis.lettuce;

import io.lettuce.core.*;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.RedisClusterURIUtil;
import io.lettuce.core.metrics.CommandLatencyRecorder;
import io.lettuce.core.protocol.ProtocolVersion;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.EventLoopGroup;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.cache.redis.lettuce.telemetry.OpentelemetryLettuceCommandLatencyRecorder;

import java.net.URI;
import java.time.Duration;
import java.util.List;

public class LettuceClientFactory {

    @Nonnull
    public AbstractRedisClient build(LettuceClientConfig config) {
        return build(config, null, null, null);
    }

    @Nonnull
    public AbstractRedisClient build(LettuceClientConfig config,
                                     @Nullable MeterRegistry meterRegistry,
                                     @Nullable LettuceConfigurator lettuceConfigurator,
                                     @Nullable EventLoopGroup eventLoopGroup) {
        final Duration commandTimeout = config.commandTimeout();
        final Duration socketTimeout = config.socketTimeout();
        final ProtocolVersion protocolVersion = switch (config.protocol()) {
            case RESP2 -> ProtocolVersion.RESP2;
            case RESP3 -> ProtocolVersion.RESP3;
        };

        final List<RedisURI> mappedRedisUris = buildRedisURI(config);

        return (mappedRedisUris.size() == 1)
            ? buildRedisClientInternal(config, mappedRedisUris.get(0), commandTimeout, socketTimeout, protocolVersion, meterRegistry, lettuceConfigurator, eventLoopGroup)
            : buildRedisClusterClientInternal(config, mappedRedisUris, commandTimeout, socketTimeout, protocolVersion, meterRegistry, lettuceConfigurator, eventLoopGroup);
    }

    @Nonnull
    public RedisClusterClient buildRedisClusterClient(LettuceClientConfig config) {
        return buildRedisClusterClient(config, null, null, null);
    }

    @Nonnull
    public RedisClusterClient buildRedisClusterClient(LettuceClientConfig config,
                                                      @Nullable MeterRegistry meterRegistry,
                                                      @Nullable LettuceConfigurator lettuceConfigurator,
                                                      @Nullable EventLoopGroup eventLoopGroup) {
        final Duration commandTimeout = config.commandTimeout();
        final Duration socketTimeout = config.socketTimeout();
        final ProtocolVersion protocolVersion = switch (config.protocol()) {
            case RESP2 -> ProtocolVersion.RESP2;
            case RESP3 -> ProtocolVersion.RESP3;
        };
        final List<RedisURI> mappedRedisUris = buildRedisURI(config);
        return buildRedisClusterClientInternal(config, mappedRedisUris, commandTimeout, socketTimeout, protocolVersion, meterRegistry, lettuceConfigurator, eventLoopGroup);
    }

    @Nonnull
    public RedisClient buildRedisClient(LettuceClientConfig config) {
        return buildRedisClient(config, null, null, null);
    }

    @Nonnull
    public RedisClient buildRedisClient(LettuceClientConfig config,
                                        @Nullable MeterRegistry meterRegistry,
                                        @Nullable LettuceConfigurator lettuceConfigurator,
                                        @Nullable EventLoopGroup eventLoopGroup) {
        final Duration commandTimeout = config.commandTimeout();
        final Duration socketTimeout = config.socketTimeout();
        final ProtocolVersion protocolVersion = switch (config.protocol()) {
            case RESP2 -> ProtocolVersion.RESP2;
            case RESP3 -> ProtocolVersion.RESP3;
        };
        final List<RedisURI> mappedRedisUris = buildRedisURI(config);
        return buildRedisClientInternal(config, mappedRedisUris.get(0), commandTimeout, socketTimeout, protocolVersion, meterRegistry, lettuceConfigurator, eventLoopGroup);
    }

    @Nonnull
    private static RedisClusterClient buildRedisClusterClientInternal(LettuceClientConfig config,
                                                                      List<RedisURI> redisURIs,
                                                                      Duration commandTimeout,
                                                                      Duration socketTimeout,
                                                                      ProtocolVersion protocolVersion,
                                                                      @Nullable MeterRegistry meterRegistry,
                                                                      @Nullable LettuceConfigurator lettuceConfigurator,
                                                                      @Nullable EventLoopGroup eventLoopGroup) {

        CommandLatencyRecorder recorder = meterRegistry != null && config.telemetry().metrics().enabled()
            ? new OpentelemetryLettuceCommandLatencyRecorder("cluster", meterRegistry, config.telemetry().metrics())
            : CommandLatencyRecorder.disabled();

        var clientResourcesBuilder = DefaultClientResources.builder()
            .commandLatencyRecorder(recorder);

        if (eventLoopGroup != null) {
            clientResourcesBuilder = clientResourcesBuilder.eventExecutorGroup(eventLoopGroup);
        }
        if (lettuceConfigurator != null) {
            clientResourcesBuilder = lettuceConfigurator.configure(clientResourcesBuilder);
        }

        final RedisClusterClient client = RedisClusterClient.create(clientResourcesBuilder.build(), redisURIs);
        var clusterBuilder = ClusterClientOptions.builder()
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

        if (lettuceConfigurator != null) {
            clusterBuilder = lettuceConfigurator.configure(clusterBuilder);
        }

        client.setOptions(clusterBuilder.build());
        return client;
    }

    @Nonnull
    private static RedisClient buildRedisClientInternal(LettuceClientConfig config,
                                                        RedisURI redisURI,
                                                        Duration commandTimeout,
                                                        Duration socketTimeout,
                                                        ProtocolVersion protocolVersion,
                                                        @Nullable MeterRegistry meterRegistry,
                                                        @Nullable LettuceConfigurator lettuceConfigurator,
                                                        @Nullable EventLoopGroup eventLoopGroup) {
        CommandLatencyRecorder recorder = meterRegistry != null && config.telemetry().metrics().enabled()
            ? new OpentelemetryLettuceCommandLatencyRecorder("single", meterRegistry, config.telemetry().metrics())
            : CommandLatencyRecorder.disabled();

        var clientResourcesBuilder = DefaultClientResources.builder()
            .commandLatencyRecorder(recorder);

        if (eventLoopGroup != null) {
            clientResourcesBuilder = clientResourcesBuilder.eventExecutorGroup(eventLoopGroup);
        }
        if (lettuceConfigurator != null) {
            clientResourcesBuilder = lettuceConfigurator.configure(clientResourcesBuilder);
        }

        final RedisClient client = RedisClient.create(clientResourcesBuilder.build(), redisURI);
        var clientBuilder = ClientOptions.builder()
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
                .fixedTimeout(commandTimeout)
                .timeoutCommands(true)
                .build())
            .socketOptions(SocketOptions.builder()
                .keepAlive(true)
                .connectTimeout(socketTimeout)
                .build());

        if (lettuceConfigurator != null) {
            clientBuilder = lettuceConfigurator.configure(clientBuilder);
        }

        client.setOptions(clientBuilder.build());
        return client;
    }

    static List<RedisURI> buildRedisURI(LettuceClientConfig config) {
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

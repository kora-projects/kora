package ru.tinkoff.kora.cache.redis.lettuce;

import io.lettuce.core.*;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.RedisClusterURIUtil;
import io.lettuce.core.protocol.ProtocolVersion;
import jakarta.annotation.Nonnull;

import java.net.URI;
import java.time.Duration;
import java.util.List;

public final class LettuceClientFactory {

    @Nonnull
    public AbstractRedisClient build(LettuceClientConfig config) {
        final Duration commandTimeout = config.commandTimeout();
        final Duration socketTimeout = config.socketTimeout();
        final ProtocolVersion protocolVersion = switch (config.protocol()) {
            case RESP2 -> ProtocolVersion.RESP2;
            case RESP3 -> ProtocolVersion.RESP3;
        };

        final List<RedisURI> mappedRedisUris = buildRedisURI(config);

        return (mappedRedisUris.size() == 1)
            ? buildRedisClientInternal(config, mappedRedisUris.get(0), commandTimeout, socketTimeout, protocolVersion)
            : buildRedisClusterClientInternal(config, mappedRedisUris, commandTimeout, socketTimeout, protocolVersion);
    }

    @Nonnull
    public RedisClusterClient buildRedisClusterClient(LettuceClientConfig config) {
        final Duration commandTimeout = config.commandTimeout();
        final Duration socketTimeout = config.socketTimeout();
        final ProtocolVersion protocolVersion = switch (config.protocol()) {
            case RESP2 -> ProtocolVersion.RESP2;
            case RESP3 -> ProtocolVersion.RESP3;
        };
        final List<RedisURI> mappedRedisUris = buildRedisURI(config);
        return buildRedisClusterClientInternal(config, mappedRedisUris, commandTimeout, socketTimeout, protocolVersion);
    }

    @Nonnull
    public RedisClient buildRedisClient(LettuceClientConfig config) {
        final Duration commandTimeout = config.commandTimeout();
        final Duration socketTimeout = config.socketTimeout();
        final ProtocolVersion protocolVersion = switch (config.protocol()) {
            case RESP2 -> ProtocolVersion.RESP2;
            case RESP3 -> ProtocolVersion.RESP3;
        };
        final List<RedisURI> mappedRedisUris = buildRedisURI(config);
        return buildRedisClientInternal(config, mappedRedisUris.get(0), commandTimeout, socketTimeout, protocolVersion);
    }

    @Nonnull
    private static RedisClusterClient buildRedisClusterClientInternal(LettuceClientConfig config,
                                                                      List<RedisURI> redisURIs,
                                                                      Duration commandTimeout,
                                                                      Duration socketTimeout,
                                                                      ProtocolVersion protocolVersion) {
        final RedisClusterClient client = RedisClusterClient.create(redisURIs);
        client.setOptions(ClusterClientOptions.builder()
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
                .build())
            .build());

        return client;
    }

    @Nonnull
    private static RedisClient buildRedisClientInternal(LettuceClientConfig config,
                                                        RedisURI redisURI,
                                                        Duration commandTimeout,
                                                        Duration socketTimeout,
                                                        ProtocolVersion protocolVersion) {
        final RedisClient client = RedisClient.create(redisURI);
        client.setOptions(ClientOptions.builder()
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
                .build())
            .build());

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

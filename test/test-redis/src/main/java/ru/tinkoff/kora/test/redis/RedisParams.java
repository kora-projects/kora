package ru.tinkoff.kora.test.redis;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.protocol.ProtocolVersion;

import java.net.URI;
import java.time.Duration;
import java.util.function.Function;

public record RedisParams(String host, int port) {

    private static volatile RedisClient client;

    public URI uri() {
        return URI.create(String.format("redis://%s:%s", host, port));
    }

    public <T> T execute(Function<RedisCommands<String, String>, T> commandsConsumer) {
        try (StatefulRedisConnection<String, String> connection = client().connect()) {
            final RedisCommands<String, String> commands = connection.sync();
            return commandsConsumer.apply(commands);
        }
    }

    private RedisClient client() {
        if (client != null) {
            return client;
        }

        final RedisURI redisURI = RedisURI.create(uri());
        final RedisClient client = RedisClient.create(redisURI);
        client.setOptions(ClientOptions.builder()
            .autoReconnect(true)
            .publishOnScheduler(true)
            .suspendReconnectOnProtocolFailure(false)
            .disconnectedBehavior(ClientOptions.DisconnectedBehavior.DEFAULT)
            .protocolVersion(ProtocolVersion.RESP3)
            .timeoutOptions(TimeoutOptions.builder()
                .fixedTimeout(Duration.ofSeconds(15))
                .timeoutCommands(true)
                .build())
            .socketOptions(SocketOptions.builder()
                .keepAlive(true)
                .connectTimeout(Duration.ofSeconds(15))
                .build())
            .build());

        RedisParams.client = client;
        return client;
    }
}

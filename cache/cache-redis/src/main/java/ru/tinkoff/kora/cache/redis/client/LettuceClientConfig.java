package ru.tinkoff.kora.cache.redis.client;

import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.protocol.ProtocolVersion;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Arrays;

public record LettuceClientConfig(String uri,
                                  @Nullable Integer database,
                                  @Nullable String user,
                                  @Nullable String password,
                                  @Nullable String protocol,
                                  @Nullable Duration socketTimeout,
                                  @Nullable Duration commandTimeout) {

    public LettuceClientConfig(String uri,
                               @Nullable Integer database,
                               @Nullable String user,
                               @Nullable String password,
                               @Nullable String protocol,
                               @Nullable Duration socketTimeout,
                               @Nullable Duration commandTimeout) {
        this.uri = uri;
        this.database = database;
        this.user = user;
        this.password = password;
        this.protocol = (protocol == null)
            ? ProtocolVersion.RESP3.name()
            : protocol;
        this.commandTimeout = (commandTimeout == null)
            ? Duration.ofSeconds(RedisURI.DEFAULT_TIMEOUT)
            : commandTimeout;
        this.socketTimeout = (socketTimeout == null)
            ? Duration.ofSeconds(SocketOptions.DEFAULT_CONNECT_TIMEOUT)
            : socketTimeout;
    }

    public ProtocolVersion protocolVersion() {
        if (ProtocolVersion.RESP3.name().equals(protocol)) {
            return ProtocolVersion.RESP3;
        } else if (ProtocolVersion.RESP2.name().equals(protocol)) {
            return ProtocolVersion.RESP2;
        } else {
            throw new IllegalArgumentException("Unknown protocol value '" + protocol
                + "', expected value one of: " + Arrays.toString(ProtocolVersion.values()));
        }
    }
}

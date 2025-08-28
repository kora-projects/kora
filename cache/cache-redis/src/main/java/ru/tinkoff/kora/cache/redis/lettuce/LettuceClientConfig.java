package ru.tinkoff.kora.cache.redis.lettuce;

import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;
import java.util.List;

@ConfigValueExtractor
public interface LettuceClientConfig {

    String uri();

    default boolean forceClusterClient() {
        return false;
    }

    @Nullable
    Integer database();

    @Nullable
    String user();

    @Nullable
    String password();

    default Protocol protocol() {
        return Protocol.RESP3;
    }

    default Duration socketTimeout() {
        return Duration.ofSeconds(SocketOptions.DEFAULT_CONNECT_TIMEOUT);
    }

    default Duration commandTimeout() {
        return Duration.ofSeconds(RedisURI.DEFAULT_TIMEOUT);
    }

    TelemetryConfig telemetry();

    enum Protocol {

        /**
         * Redis 2 to Redis 5
         */
        RESP2,
        /**
         * Redis 6+
         */
        RESP3
    }

    SslConfig ssl();

    @ConfigValueExtractor
    interface SslConfig {

        default List<String> ciphers() {
            return List.of();
        }

        default Duration handshakeTimeout() {
            return Duration.ofSeconds(10);
        }
    }
}

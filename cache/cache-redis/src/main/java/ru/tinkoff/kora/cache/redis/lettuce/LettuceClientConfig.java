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

    /**
     * @return URI for connecting to Redis, single or multiple servers, optionally with SSL or TLS scheme.
     */
    String uri();

    /**
     * @return Whether to create a cluster client even when a single connection URI is specified.
     */
    default boolean forceClusterClient() {
        return false;
    }

    /**
     * @return Database number for the connection.
     */
    @Nullable
    Integer database();

    /**
     * @return Username for the connection.
     */
    @Nullable
    String user();

    /**
     * @return User password for the connection.
     */
    @Nullable
    String password();

    /**
     * @return Protocol used to communicate with Redis.
     */
    default Protocol protocol() {
        return Protocol.RESP3;
    }

    /**
     * @return Socket connection timeout.
     */
    default Duration socketTimeout() {
        return Duration.ofSeconds(SocketOptions.DEFAULT_CONNECT_TIMEOUT);
    }

    /**
     * @return Command execution timeout.
     */
    default Duration commandTimeout() {
        return Duration.ofSeconds(RedisURI.DEFAULT_TIMEOUT);
    }

    /**
     * @return Telemetry configuration of logging, metrics and tracing for the module.
     */
    TelemetryConfig telemetry();

    enum Protocol {

        /** Redis 2 to Redis 5 */
        RESP2,
        /** Redis 6+ */
        RESP3
    }

    /**
     * @return Configuration of the secure connection between client and server.
     */
    SslConfig ssl();

    @ConfigValueExtractor
    interface SslConfig {

        /**
         * @return Cipher algorithms for a secure connection between client and server.
         */
        default List<String> ciphers() {
            return List.of();
        }

        /**
         * @return Timeout for establishing a secure connection with the server.
         */
        default Duration handshakeTimeout() {
            return Duration.ofSeconds(10);
        }
    }
}

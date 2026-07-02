package io.koraframework.redis.lettuce;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.redis.lettuce.telemetry.LettuceTelemetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;

@ConfigMapper
public interface LettuceConfig {

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
        return Duration.ofSeconds(10);
    }

    default Duration commandTimeout() {
        return Duration.ofSeconds(30);
    }

    LettuceTelemetryConfig telemetry();

    SslConfig ssl();

    @ConfigMapper
    interface SslConfig {

        default List<String> ciphers() {
            return List.of();
        }

        default Duration handshakeTimeout() {
            return Duration.ofSeconds(10);
        }
    }

    enum Protocol {

        /** Redis 2 to Redis 5 */
        RESP2,
        /** Redis 6+ */
        RESP3
    }
}
